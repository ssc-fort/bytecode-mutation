package io.mutator.javassist;

import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token types that can be embedded in {@code .jsnippet} files to mark values
 * that should be replaced with seeded-random strings at load time.
 *
 * <h3>Syntax</h3>
 * <p>A token is written as {@code #TOKEN_NAME#} directly in the snippet body,
 * typically inside a Java string literal:
 * <pre>
 *   Class cls = Class.forName("#CLASSNAME#");
 * </pre>
 * {@code #} is not a valid Java source character outside string literals, making
 * the format unambiguous and impossible to confuse with real Java code.
 *
 * <h3>Generation strategy</h3>
 * <p>Each constant implements {@link #generate(Random)} to produce a string that
 * is structurally appropriate for its type (class name, URL, etc.).  The word
 * components are drawn from Java's built-in timezone database
 * ({@link TimeZone#getAvailableIDs()}) — city and region names like
 * {@code tokyo}, {@code london}, {@code newyork} — which acts as an
 * unguessable runtime word source requiring no external dependency.
 *
 * <p>Because the strings are generated rather than selected from a hardcoded
 * list, no target strings appear in this source file, and a different seed
 * produces a completely different set of strings across all snippets.
 *
 * <h3>Substitution</h3>
 * <p>Call {@link #substituteAll(String, Random)} to replace every token in a
 * snippet body.  Tokens are replaced in document order and the same
 * {@link Random} instance is advanced once per token, so the full substitution
 * is reproducible for any given seed.  Unknown token names are left in place so
 * they surface as a Javassist compilation error rather than silently producing
 * wrong code.
 */
public enum SnippetToken {

    /** Fully-qualified class name suitable for reflection APIs. */
    CLASSNAME {
        @Override
        public String generate(Random rng) {
            // e.g. "com.london.Tokyo" — looks like a real (if unusual) package + class
            return "com." + word(rng) + "." + capitalize(word(rng));
        }
    },

    /** HTTP or HTTPS URL that looks like internal infrastructure. */
    HTTP_URL {
        @Override
        public String generate(Random rng) {
            String   scheme = rng.nextBoolean() ? "http" : "https";
            String[] tlds   = {"com", "io", "net", "org", "dev"};
            return scheme + "://" + word(rng) + "." + word(rng) + "."
                    + tlds[rng.nextInt(tlds.length)];
        }
    },

    /** Absolute path to a file on a Unix system. */
    FILEPATH {
        @Override
        public String generate(Random rng) {
            String[] prefixes = {"/etc/", "/home/user/.", "/var/log/", "/tmp/."};
            return prefixes[rng.nextInt(prefixes.length)] + word(rng);
        }
    },

    /** OS command string compatible with {@code Runtime.exec()}. */
    COMMAND {
        @Override
        public String generate(Random rng) {
            String[] verbs = {"ls", "cat", "find", "curl", "grep", "echo"};
            return verbs[rng.nextInt(verbs.length)] + " /tmp/" + word(rng);
        }
    },

    /** Generic string value, usable wherever a string literal is expected. */
    STRING {
        @Override
        public String generate(Random rng) {
            return word(rng) + "_" + word(rng);
        }
    },

    /**
     * A {@code byte[]} expression — a random string converted via
     * {@code getBytes()}.  Use <em>without</em> surrounding quotes in the
     * snippet so the full expression (including the literal quotes) is emitted:
     * <pre>
     *   new javax.crypto.spec.SecretKeySpec(#BYTES#, "AES")
     * </pre>
     * produces e.g.
     * {@code new javax.crypto.spec.SecretKeySpec("london_jakarta".getBytes(), "AES")}.
     */
    BYTES {
        @Override
        public String generate(Random rng) {
            return "\"" + word(rng) + "_" + word(rng) + "\".getBytes()";
        }
    },

    /**
     * A small positive integer literal.  Suitable for port numbers, numeric
     * constants, and any context expecting an {@code int} or {@code long}
     * (Java auto-promotes).  Use without quotes:
     * <pre>
     *   new java.net.Socket("#STRING#", #INT#)
     * </pre>
     */
    INT {
        @Override
        public String generate(Random rng) {
            // Values in the port-number range look natural in code.
            return String.valueOf(1024 + rng.nextInt(64512));
        }
    };

    // -----------------------------------------------------------------------
    // Pattern and substitution
    // -----------------------------------------------------------------------

    /** Matches any token of the form {@code #UPPER_CASE_NAME#}. */
    private static final Pattern PATTERN = Pattern.compile("#([A-Z_]+)#");

    /**
     * Produces the replacement string for this token type using {@code rng} as
     * the source of randomness.  The same seed always produces the same string.
     */
    public abstract String generate(Random rng);

    /**
     * Replaces every {@code #TOKEN_NAME#} occurrence in {@code body} with a
     * generated string, advancing {@code rng} once per token found.
     * Tokens are replaced in the order they appear in the text.
     *
     * <p>An unrecognised token name is left unchanged so that it triggers a
     * Javassist compilation error rather than silently injecting wrong code.
     */
    public static String substituteAll(String body, Random rng) {
        Matcher       m  = PATTERN.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            try {
                replacement = SnippetToken.valueOf(m.group(1)).generate(rng);
            } catch (IllegalArgumentException e) {
                replacement = m.group(0); // leave unknown token intact
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Word generation via Java's built-in timezone database
    // -----------------------------------------------------------------------

    /**
     * All timezone IDs provided by the JRE — e.g. "America/New_York",
     * "Asia/Tokyo", "Europe/London".  Loaded once; available on every JRE
     * with no external dependency.
     */
    private static final String[] TIMEZONE_IDS = TimeZone.getAvailableIDs();

    /**
     * Returns a lowercase word derived from a randomly chosen timezone ID.
     * The city portion is extracted ("America/New_York" → "newyork") and
     * punctuation is stripped, producing natural-looking identifier segments.
     */
    private static String word(Random rng) {
        String id    = TIMEZONE_IDS[rng.nextInt(TIMEZONE_IDS.length)];
        int    slash = id.lastIndexOf('/');
        String city  = slash >= 0 ? id.substring(slash + 1) : id;
        return city.replace("_", "").replace("-", "").toLowerCase();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
