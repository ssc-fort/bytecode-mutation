package io.mutator.javassist;

import net.java.quickcheck.Generator;
import net.java.quickcheck.generator.PrimitiveGenerators;
import net.java.quickcheck.generator.distribution.RandomConfiguration;
import net.java.quickcheck.generator.support.FixedValuesGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token types that can be embedded in {@code .jsnippet} files to mark values
 * that should be replaced with generated candidates at injection time.
 *
 * <p>This handles the original untyped {@code #TOKEN#} format; the typed
 * {@code <name>} / {@code // @in} format is handled by
 * {@link PlaceholderSubstitutor}.  {@link JavassistMutator} dispatches to
 * whichever format a snippet uses, so both coexist.
 *
 * <h3>Syntax and positions</h3>
 * <p>A token is written as {@code #TOKEN_NAME#} directly in the snippet body.
 * The substitution recognises three positions, decided by the double quotes (if
 * any) immediately surrounding the token:
 * <ul>
 *   <li><b>Standalone</b> — {@code "#STRING#"} (both quotes) or a bare token in
 *       expression position ({@code foo(#STRING#)}, neither quote).  The token
 *       expands to a complete Java expression: a quoted string literal, or — for
 *       string-valued tokens — a {@code String} parameter of the target method.
 *   <li><b>Embedded</b> — exactly one adjacent quote, i.e. the token sits inside
 *       a larger string literal ({@code "@myorg:registry=#HTTP_URL#"}).  Only the
 *       bare text is substituted; the surrounding literal's quote is preserved.
 *   <li><b>Expression tokens</b> — {@link #BYTES} and {@link #INT} always emit a
 *       complete expression and are written without quotes.
 * </ul>
 * <pre>
 *   Class cls = Class.forName("#CLASSNAME#");      // → Class.forName("com.tokyo.Bar")
 *   bw.write("@myorg:registry=#HTTP_URL#");        // → "@myorg:registry=https://a.b.io"
 *   byte[] k = #BYTES#;                            // → byte[] k = "alpha_beta".getBytes()
 *   new java.net.Socket("#STRING#", #INT#)         // → new java.net.Socket("a_b", 5412)
 * </pre>
 *
 * <h3>Value generation (QuickCheck)</h3>
 * <p>Values are produced by <a href="http://java.net/projects/quickcheck">QuickCheck</a>
 * generators ({@code net.java.quickcheck}).  String fragments are drawn from a
 * 10,000-word dictionary ({@code words-10k.txt} on the classpath) via a
 * {@link FixedValuesGenerator}; integers come from
 * {@link PrimitiveGenerators#integers(int, int)}.  QuickCheck draws from a single
 * process-global RNG, so {@link #substituteAll(String, long, List)} re-seeds it
 * (under a lock) at the start of every call: the same {@code (body, seed,
 * stringParams)} therefore always produces identical output regardless of what
 * else has run.
 *
 * <h3>Method-argument candidates</h3>
 * <p>String-valued tokens ({@link #stringTyped()} {@code == true}) may also be
 * satisfied by a parameter of the target behavior.  When the enclosing method or
 * constructor declares one or more {@code String} parameters, there is a flat
 * <b>50% chance</b> the token resolves to a parameter (one of its Javassist
 * references {@code $1}, {@code $2}, … chosen uniformly) and a 50% chance it uses
 * a freshly generated value.  The split is deliberately independent of the
 * parameter count: a simple pooled choice would make methods with more
 * {@code String} parameters use parameters more often (N of N+1 candidates),
 * biasing the generated training data.  This lets injected code consume live
 * values from the surrounding environment rather than only constants.  Because a
 * {@code String} parameter is type-compatible with every string token context
 * (file path, URL, command, class name, …) the substitution always compiles, and
 * when no {@code String} parameters exist the generated value is always used.
 *
 * <h3>Substitution</h3>
 * <p>{@link #substituteAll(String, long, List)} replaces every token in document
 * order.  Unknown token names are left in place so they surface as a Javassist
 * compilation error rather than silently producing wrong code.
 */
public enum SnippetToken {

    /** Fully-qualified class name suitable for reflection APIs. */
    CLASSNAME {
        @Override
        String core() {
            // e.g. com.tokyo.Harbor — looks like a real (if unusual) package + class
            return "com." + word() + "." + capitalize(word());
        }
    },

    /** HTTP or HTTPS URL that looks like internal infrastructure. */
    HTTP_URL {
        @Override
        String core() {
            String   scheme = bounded(2) == 0 ? "http" : "https";
            String[] tlds   = {"com", "io", "net", "org", "dev"};
            return scheme + "://" + word() + "." + word() + "." + tlds[bounded(tlds.length)];
        }
    },

    /** Absolute path to a file on a Unix system. */
    FILEPATH {
        @Override
        String core() {
            String[] prefixes = {"/etc/", "/home/user/.", "/var/log/", "/tmp/."};
            return prefixes[bounded(prefixes.length)] + word();
        }
    },

    /** OS command string compatible with {@code Runtime.exec()}. */
    COMMAND {
        @Override
        String core() {
            String[] verbs = {"ls", "cat", "find", "curl", "grep", "echo"};
            return verbs[bounded(verbs.length)] + " /tmp/" + word();
        }
    },

    /** Generic string value, usable wherever a string literal is expected. */
    STRING {
        @Override
        String core() {
            return word() + "_" + word();
        }
    },

    /**
     * A {@code byte[]} expression — a random string converted via
     * {@code getBytes()}.  An <em>expression</em> token: {@link #core()} already
     * returns a complete expression including its own quotes, so it is written
     * without surrounding quotes in the snippet:
     * <pre>
     *   new javax.crypto.spec.SecretKeySpec(#BYTES#, "AES")
     * </pre>
     * produces e.g.
     * {@code new javax.crypto.spec.SecretKeySpec("alpha_beta".getBytes(), "AES")}.
     * Not string-typed: a {@code String} parameter is not a {@code byte[]}, so
     * method arguments are never substituted here.
     */
    BYTES {
        @Override
        String core() {
            return quote(word() + "_" + word()) + ".getBytes()";
        }
        @Override
        boolean expression() {
            return true;
        }
        @Override
        boolean stringTyped() {
            return false;
        }
    },

    /**
     * A small positive integer literal.  An <em>expression</em> token suitable
     * for port numbers, numeric constants, and any context expecting an
     * {@code int} or {@code long} (Java auto-promotes).  Written without quotes:
     * <pre>
     *   new java.net.Socket("#STRING#", #INT#)
     * </pre>
     */
    INT {
        @Override
        String core() {
            // Values in the port-number range look natural in code.
            return String.valueOf(randomInt());
        }
        @Override
        boolean expression() {
            return true;
        }
        @Override
        boolean stringTyped() {
            return false;
        }
    };

    // -----------------------------------------------------------------------
    // Per-constant behaviour
    // -----------------------------------------------------------------------

    /**
     * Produces this token's bare value using the (already-seeded) QuickCheck RNG.
     * For string-valued tokens this is the unquoted text (the substitution adds
     * quotes when needed); for {@link #expression() expression} tokens it is a
     * complete Java expression including any quoting it requires.
     */
    abstract String core();

    /**
     * Whether {@link #core()} is a complete Java expression that must never be
     * quoted ({@link #BYTES}, {@link #INT}).  {@code false} for string-valued
     * tokens, whose core is plain text destined for inside quotes.
     */
    boolean expression() {
        return false;
    }

    /**
     * Whether a {@code String} parameter of the target behavior is a valid
     * substitution for this token.  {@code true} for all string-valued tokens;
     * {@code false} for {@link #BYTES} and {@link #INT}.
     */
    boolean stringTyped() {
        return true;
    }

    // -----------------------------------------------------------------------
    // Pattern and substitution
    // -----------------------------------------------------------------------

    /**
     * Matches a token {@code #UPPER_CASE_NAME#}, optionally including a single
     * surrounding pair of double quotes.  The quotes are consumed as part of the
     * match so that a chosen parameter reference ({@code $1}) replaces them
     * cleanly, while a chosen string literal re-supplies its own quoting.
     */
    private static final Pattern PATTERN = Pattern.compile("\"?#([A-Z_]+)#\"?");

    /**
     * Replaces every token in {@code body} with a generated expression,
     * re-seeding the QuickCheck RNG with {@code seed} first so the result is
     * fully reproducible.  When a string-valued token is in expression position
     * and {@code stringParams} is non-empty, there is a 50% chance it resolves to
     * one of those parameter references (chosen uniformly) and a 50% chance it
     * uses a freshly generated value — independent of how many parameters exist.
     *
     * <p>{@code synchronized} because QuickCheck's RNG is process-global: the
     * lock keeps the {@code setSeed}-then-generate sequence atomic so concurrent
     * callers cannot interleave and perturb each other's draws.
     *
     * @param body         snippet source containing {@code #TOKEN#} placeholders
     * @param seed         seed for reproducible value generation
     * @param stringParams candidate parameter references for string tokens
     *                     (may be empty, never {@code null})
     * @return {@code body} with every recognised token replaced
     */
    public static String substituteAll(String body, long seed, List<String> stringParams) {
      synchronized (RandomValues.RNG_LOCK) {
        RandomConfiguration.setSeed(seed);

        Matcher       m  = PATTERN.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String  match = m.group(0);
            boolean lead  = match.startsWith("\"");
            boolean trail = match.endsWith("\"");

            String replacement;
            try {
                SnippetToken token = SnippetToken.valueOf(m.group(1));
                String       core  = token.core();

                if (token.expression()) {
                    // core is a full expression; re-emit any quotes we matched
                    // so an accidental embedding stays syntactically balanced.
                    replacement = requote(core, lead, trail);
                } else if (lead ^ trail) {
                    // exactly one quote → embedded inside a larger string literal:
                    // splice the bare text in and preserve that literal's quote.
                    replacement = requote(core, lead, trail);
                } else {
                    // standalone-quoted ("#T#") or bare expression position:
                    // emit a full String expression — a quoted literal, or a
                    // String parameter of the target method when one exists.
                    String literal = quote(core);
                    if (!stringParams.isEmpty() && bounded(2) == 0) {
                        // 50/50 between "use a parameter" and "use a generated
                        // value", independent of how many parameters exist; the
                        // parameter itself is then chosen uniformly. A fixed coin
                        // flip avoids biasing methods that declare more String
                        // parameters toward parameter use, which would skew the
                        // distribution of the training data.
                        replacement = new FixedValuesGenerator<>(stringParams).next();
                    } else {
                        replacement = literal;
                    }
                }
            } catch (IllegalArgumentException e) {
                replacement = match; // leave unknown token (and its quotes) intact
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
      }
    }

    // -----------------------------------------------------------------------
    // QuickCheck-backed value sources
    // -----------------------------------------------------------------------

    /** Classpath location of the dictionary used for string fragments. */
    private static final String WORDS_RESOURCE = "/words-10k.txt";

    /** The dictionary, loaded once from the classpath. */
    private static final List<String> WORDS = loadWords();

    /** Uniform picker over {@link #WORDS}; draws from QuickCheck's global RNG. */
    private static final Generator<String> WORD_GEN = new FixedValuesGenerator<>(WORDS);

    /** Port-range integer generator. */
    private static final Generator<Integer> INT_GEN = PrimitiveGenerators.integers(1024, 65535);

    /** Returns a random dictionary word. */
    private static String word() {
        return WORD_GEN.next();
    }

    /** Returns a random port-range integer. */
    private static int randomInt() {
        return INT_GEN.next();
    }

    /** Returns a uniform random index in {@code [0, n)}. */
    private static int bounded(int n) {
        return PrimitiveGenerators.integers(0, n - 1).next();
    }

    /** Wraps {@code s} in double quotes to form a Java string literal. */
    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    /** Re-attaches a leading and/or trailing quote that the pattern consumed. */
    private static String requote(String s, boolean lead, boolean trail) {
        return (lead ? "\"" : "") + s + (trail ? "\"" : "");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Loads the comma-separated dictionary from {@link #WORDS_RESOURCE} on the
     * classpath.  Whitespace around each word is stripped and empty entries are
     * skipped.
     *
     * @throws IllegalStateException if the resource is missing or empty
     */
    private static List<String> loadWords() {
        try (InputStream in = SnippetToken.class.getResourceAsStream(WORDS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Dictionary resource not found on classpath: " + WORDS_RESOURCE);
            }
            String       raw   = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String token : raw.split(",")) {
                String w = token.strip();
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            if (words.isEmpty()) {
                throw new IllegalStateException(
                        "Dictionary resource is empty: " + WORDS_RESOURCE);
            }
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dictionary " + WORDS_RESOURCE, e);
        }
    }
}
