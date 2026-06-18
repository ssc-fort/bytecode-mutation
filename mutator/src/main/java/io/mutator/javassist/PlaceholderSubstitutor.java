package io.mutator.javassist;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code <name>} placeholders in a snippet that uses the typed
 * {@code // @in} declaration format, e.g.
 *
 * <pre>
 *   // @in &lt;url&gt; java.lang.String url
 *   // @in &lt;port&gt; int tcp-port
 *   new java.net.Socket(&lt;url&gt;, &lt;port&gt;);
 * </pre>
 *
 * <p>Each declaration names a placeholder, its Java type, and a semantic tag.
 * Substitution:
 * <ul>
 *   <li>generates <b>one</b> value per declared placeholder and reuses it at
 *       every occurrence, so repeated placeholders (e.g. a key referenced as
 *       {@code <key>[i % <key>.length]}) stay consistent;</li>
 *   <li>may instead use a <b>method parameter of the matching Java type</b>: when
 *       the target behaviour declares a parameter whose type equals the
 *       placeholder's, there is a 50% chance the placeholder resolves to that
 *       parameter reference ({@code $1}, …) rather than a generated value (a
 *       flat 50/50 regardless of how many candidates exist, to avoid biasing the
 *       data; {@code void} statement placeholders never use a parameter);</li>
 *   <li>chooses the generated value from {@link Generators}, keyed by semantic
 *       tag with a fall-back keyed by Java type;</li>
 *   <li>strips the {@code @in} comment lines from the returned code.</li>
 * </ul>
 *
 * <p>Like {@link SnippetToken}, generation draws from QuickCheck's global RNG
 * under {@link RandomValues#RNG_LOCK} after re-seeding, so the same
 * {@code (body, seed, params)} always produces identical output.
 */
final class PlaceholderSubstitutor {

    private PlaceholderSubstitutor() {}

    /** A parsed {@code // @in <name> <javaType> <tag>} declaration. */
    record Declaration(String name, String javaType, String tag) {}

    /** Matches one {@code // @in <name> <javaType> <tag>} line (and consumes its line break). */
    private static final Pattern AT_IN = Pattern.compile(
            "(?m)^[ \\t]*//[ \\t]*@in[ \\t]+<(\\w+)>[ \\t]+(\\S+)[ \\t]+(\\S+)[ \\t]*\\R?");

    /** True if the snippet uses the {@code @in} placeholder format. */
    static boolean hasDeclarations(String body) {
        return AT_IN.matcher(body).find();
    }

    /**
     * Substitutes every {@code <name>} placeholder declared via {@code @in} and
     * returns the resulting Java code with the {@code @in} comment lines removed.
     *
     * @param body         raw snippet source, including the {@code @in} comments
     * @param seed         reproducibility seed
     * @param paramsByType target behaviour's parameter references grouped by
     *                     exact Java type name (e.g. {@code "java.lang.String" ->
     *                     ["$1","$3"]}); never {@code null}
     */
    static String substitute(String body, long seed, Map<String, List<String>> paramsByType) {
        synchronized (RandomValues.RNG_LOCK) {
            RandomValues.seed(seed);

            // Parse declarations in document order for a stable RNG draw sequence.
            Map<String, Declaration> declarations = new LinkedHashMap<>();
            Matcher decl = AT_IN.matcher(body);
            while (decl.find()) {
                declarations.put(decl.group(1),
                        new Declaration(decl.group(1), decl.group(2), decl.group(3)));
            }

            // One value per placeholder, reused at every occurrence.
            Map<String, String> valueByName = new LinkedHashMap<>();
            for (Declaration d : declarations.values()) {
                valueByName.put(d.name(), chooseValue(d, paramsByType));
            }

            String code = AT_IN.matcher(body).replaceAll("").strip();
            for (Map.Entry<String, String> e : valueByName.entrySet()) {
                code = code.replace("<" + e.getKey() + ">", e.getValue());
            }
            return code;
        }
    }

    /**
     * Returns either a matching method-parameter reference (50% when one exists)
     * or a freshly generated value for {@code d}.
     */
    private static String chooseValue(Declaration d, Map<String, List<String>> paramsByType) {
        // Generate the value first so the parameter-vs-value coin flip is never
        // the very first draw after re-seeding (which would correlate with the
        // seed); this also mirrors SnippetToken's ordering.
        String       generated   = Generators.generate(d);
        List<String> params      = paramsByType.get(d.javaType());
        boolean      canUseParam = params != null && !params.isEmpty() && !"void".equals(d.javaType());
        if (canUseParam && RandomValues.coin()) {
            return RandomValues.oneOf(params);
        }
        return generated;
    }

    // -----------------------------------------------------------------------
    // Value generators
    // -----------------------------------------------------------------------

    /**
     * Produces a complete Java expression (or, for {@code void}, a statement) for
     * a placeholder.  Resolution is by semantic tag first, then by Java type.
     */
    static final class Generators {

        private Generators() {}

        private static final Map<String, Supplier<String>> BY_TAG = new HashMap<>();
        static {
            // --- java.lang.String, with a structurally appropriate shape ---
            BY_TAG.put("url",                  Generators::url);
            BY_TAG.put("file-path",            Generators::filePath);
            BY_TAG.put("directory-path",       Generators::dirPath);
            BY_TAG.put("native-library-path",  Generators::libPath);
            BY_TAG.put("java-class-name",      Generators::className);
            BY_TAG.put("shell-command",        Generators::command);
            BY_TAG.put("base64-string",        Generators::base64);
            BY_TAG.put("java-method-name",     Generators::identifier);
            BY_TAG.put("java-field-name",      Generators::identifier);
            BY_TAG.put("hostname",             Generators::hostname);
            BY_TAG.put("env-var-name",         Generators::envVar);
            BY_TAG.put("system-property-name", Generators::sysProp);
            BY_TAG.put("script-engine-name",   Generators::engineName);
            BY_TAG.put("file-suffix",          Generators::fileSuffix);
            // Generic strings (payload, string-fragment, hash-input, github-token,
            // script-source, …) fall through to the java.lang.String type default.

            // --- non-String semantic tags ---
            BY_TAG.put("java-return-type",     Generators::classLiteral);
            BY_TAG.put("java-class",           Generators::classLiteral);
            BY_TAG.put("java-object",          Generators::objectInstance);
            BY_TAG.put("java-statement",       Generators::statement);
            // byte[] tags (xor-key, crypto-key, …), int/long tags and public-key
            // are handled by the Java-type default below.
        }

        static String generate(Declaration d) {
            Supplier<String> byTag = BY_TAG.get(d.tag());
            return byTag != null ? byTag.get() : byType(d.javaType());
        }

        /** Default generator for a Java type when the semantic tag is generic/unknown. */
        private static String byType(String javaType) {
            switch (javaType) {
                case "java.lang.String": return string();
                case "byte[]":           return bytes();
                case "int":              return String.valueOf(RandomValues.intInRange(1024, 65535));
                case "long":             return RandomValues.longValue() + "L";
                case "java.lang.Class":  return classLiteral();
                case "java.lang.Object": return objectInstance();
                case "void":             return statement();
                default:
                    // Any other reference type (e.g. java.security.PublicKey): a
                    // null cast always compiles and produces valid bytecode.
                    return "(" + javaType + ") null";
            }
        }

        // -- String shapes --------------------------------------------------

        private static String string() {
            return RandomValues.quote(RandomValues.word() + "_" + RandomValues.word());
        }

        private static String url() {
            String scheme = RandomValues.coin() ? "http" : "https";
            String tld    = RandomValues.oneOf("com", "io", "net", "org", "dev");
            return RandomValues.quote(scheme + "://" + RandomValues.word() + "." + RandomValues.word() + "." + tld);
        }

        private static String hostname() {
            String tld = RandomValues.oneOf("com", "io", "net", "org", "local");
            return RandomValues.quote(RandomValues.word() + "." + RandomValues.word() + "." + tld);
        }

        private static String filePath() {
            String prefix = RandomValues.oneOf("/etc/", "/home/user/.", "/var/log/", "/tmp/.");
            return RandomValues.quote(prefix + RandomValues.word());
        }

        private static String dirPath() {
            String prefix = RandomValues.oneOf("/etc/", "/var/log/", "/home/user/", "/tmp/", "/opt/");
            return RandomValues.quote(prefix + RandomValues.word());
        }

        private static String libPath() {
            String prefix = RandomValues.oneOf("/usr/lib/", "/lib/", "/tmp/");
            return RandomValues.quote(prefix + "lib" + RandomValues.word() + ".so");
        }

        private static String className() {
            return RandomValues.quote("com." + RandomValues.word() + "." + RandomValues.capitalize(RandomValues.word()));
        }

        private static String command() {
            String verb = RandomValues.oneOf("ls", "cat", "find", "curl", "grep", "echo");
            return RandomValues.quote(verb + " /tmp/" + RandomValues.word());
        }

        private static String base64() {
            String raw = RandomValues.word() + "_" + RandomValues.word();
            String b64 = java.util.Base64.getEncoder()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return RandomValues.quote(b64);
        }

        private static String identifier() {
            return RandomValues.quote(RandomValues.oneOf(
                    "toString", "hashCode", "equals", "getValue", "run",
                    "init", "read", "write", "close", "size"));
        }

        private static String envVar() {
            return RandomValues.quote(RandomValues.oneOf(
                    "PATH", "HOME", "USER", "SHELL", "TMPDIR", "JAVA_HOME", "LANG", "PWD"));
        }

        private static String sysProp() {
            return RandomValues.quote(RandomValues.oneOf(
                    "os.name", "user.home", "java.version", "user.dir",
                    "java.io.tmpdir", "file.separator", "user.name"));
        }

        private static String engineName() {
            return RandomValues.quote(RandomValues.oneOf("nashorn", "js", "JavaScript", "graal.js"));
        }

        private static String fileSuffix() {
            return RandomValues.quote(RandomValues.oneOf(
                    ".txt", ".log", ".key", ".env", ".dat", ".tmp", ".bin"));
        }

        // -- non-String shapes ---------------------------------------------

        private static String bytes() {
            return RandomValues.quote(RandomValues.word() + "_" + RandomValues.word()) + ".getBytes()";
        }

        private static String classLiteral() {
            return RandomValues.oneOf(
                    "Object.class", "String.class", "Integer.class",
                    "Number.class", "CharSequence.class", "Runnable.class");
        }

        private static String objectInstance() {
            return "new java.lang.Object()";
        }

        private static String statement() {
            return "System.err.println(" + string() + ");";
        }
    }
}
