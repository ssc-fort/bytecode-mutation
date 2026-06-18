package io.mutator;

import io.mutator.javassist.JavassistMutation;
import io.mutator.javassist.JavassistMutator;
import io.mutator.javassist.SnippetLoader;
import io.mutator.pitest.PitMutations;
import io.mutator.pitest.PitestMutator;
import io.mutator.security.SecurityMutations;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    /**
     * A mutation family that can be applied during a batch run.
     *
     * <p>The optional seventh CLI argument selects any combination as a
     * comma-separated list — e.g. {@code javassist}, {@code pit,security}, or
     * {@code all} (the default, equivalent to all three).  See
     * {@link #parse(String)}.
     */
    enum MutationStrategy {
        /** Javassist snippet mutations (loaded from the snippets directory). */
        JAVASSIST,
        /** PIT conditional/boundary mutations ({@link PitMutations}). */
        PIT,
        /** Security-focused bytecode mutations ({@link SecurityMutations}). */
        SECURITY;

        /**
         * Parses a comma-separated strategy list into the set of selected
         * families.  Tokens are case-insensitive and surrounding whitespace is
         * ignored; {@code all} expands to every family.  Empty tokens are
         * skipped, but the overall result must be non-empty.
         *
         * @throws IllegalArgumentException if a token is unrecognised or the list
         *         selects no families
         */
        static Set<MutationStrategy> parse(String s) {
            Set<MutationStrategy> selected = EnumSet.noneOf(MutationStrategy.class);
            for (String part : s.split(",")) {
                String token = part.trim().toLowerCase();
                if (token.isEmpty()) {
                    continue;
                }
                switch (token) {
                    case "javassist" -> selected.add(JAVASSIST);
                    case "pit"       -> selected.add(PIT);
                    case "security"  -> selected.add(SECURITY);
                    case "all"       -> selected.addAll(EnumSet.allOf(MutationStrategy.class));
                    default -> throw new IllegalArgumentException(
                            "Unknown mutation strategy '" + token
                            + "' — expected javassist, pit, security, or all");
                }
            }
            if (selected.isEmpty()) {
                throw new IllegalArgumentException(
                        "No mutation strategy selected in '" + s
                        + "' — expected a comma-separated list of javassist, pit, security, or all");
            }
            return selected;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println(
                    "Usage: mutator <input-list-file> <input-root> <output-root> <output-tsv-file>"
                    + " [<snippets-root>] [<seed>]"
                    + " [<mutations: comma-separated javassist,pit,security,all>]");
            System.exit(1);
        }

        List<Path> inputPaths    = readInputList(Paths.get(args[0]));
        Path       inputRoot     = Paths.get(args[1]).toAbsolutePath();
        Path       outputRoot    = Paths.get(args[2]).toAbsolutePath();
        Path       outputTsvPath = Paths.get(args[3]);
        Path       snippetsRoot  = Paths.get(args.length > 4 ? args[4] : "snippets");
        long       seed          = args.length > 5 ? Long.parseLong(args[5]) : 591L;
        Set<MutationStrategy> strategies = args.length > 6
                ? MutationStrategy.parse(args[6])
                : EnumSet.allOf(MutationStrategy.class);

        // Load snippets once — fails fast with a clear message before touching any input files.
        Map<String, JavassistMutation> snippets = loadSnippets(snippetsRoot, strategies);

        // Build the combined names list once — Javassist from the loaded map, PIT/security from enums.
        List<String> mutationNames = buildMutationNames(snippets, strategies);
        if (mutationNames.isEmpty()) {
            System.err.println("[ERROR] No mutations available for strategies: " + strategies);
            System.exit(1);
        }
        System.out.printf("Loaded %d mutations (%s)%n", mutationNames.size(), strategies);

        // Separate RNG for mutation selection — advanced once per input file so
        // the choice for every file is reproducible from the seed alone.
        Random selectionRng = new Random(seed);

        long startTime = System.nanoTime();

        try (PrintWriter tsv = new PrintWriter(Files.newBufferedWriter(outputTsvPath))) {
            tsv.println(buildTsvHeader());

            for (Path inputPath : inputPaths) {
                // Advance RNG before the try/catch so the chosen mutation name is
                // always known — even when an exception fires — and every file
                // consumes exactly one RNG step regardless of outcome.
                String chosenName = mutationNames.get(selectionRng.nextInt(mutationNames.size()));
                String inputRel   = inputRoot.relativize(inputPath.toAbsolutePath()).toString();
                try {
                    byte[] classBytes = readClassFile(inputPath);
                    Path   classRoot  = PitestMutator.computeClassRoot(inputPath, classBytes);
                    String row = buildTsvRow(inputPath, classBytes, classRoot,
                            inputRoot, outputRoot, seed, chosenName, snippets);
                    if (row != null) {
                        tsv.println(row);
                    }
                } catch (Exception e) {
                    System.err.printf("[ERROR] %s (%s): %s%n", inputPath, chosenName, e.getMessage());
                    String message = sanitizeMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                    tsv.println(inputRel + "\t\t" + chosenName + "\t\t\terror\t" + message);
                }
                tsv.flush(); // surface progress incrementally
            }
        }

        long endTime = System.nanoTime();
        String time = formatDuration(endTime - startTime);

        System.out.println("Completed in " + time);
        System.out.printf("Results written to %s%n", outputTsvPath);
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    /**
     * Reads a list of {@code .class} file paths from a plain-text file,
     * one path per line.  Blank lines and lines beginning with {@code #}
     * are ignored.
     */
    static List<Path> readInputList(Path listFile) throws IOException {
        return Files.readAllLines(listFile).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(Paths::get)
                .collect(Collectors.toList());
    }

    /**
     * Reads a {@code .class} file from disk and logs the byte count.
     *
     * @throws IOException if the file does not exist or cannot be read
     */
    static byte[] readClassFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        System.out.printf("Read %d bytes from %s%n", bytes.length, path);
        return bytes;
    }

    // -----------------------------------------------------------------------
    // Verification
    // -----------------------------------------------------------------------

    /**
     * Verifies mutated bytecode using a {@link URLClassLoader} rooted at
     * {@code classRoot} so that sibling library types can be resolved during
     * the data-flow analysis pass.
     */
    static Optional<String> verifyBytecode(byte[] bytes, Path classRoot) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{ classRoot.toAbsolutePath().toFile().toURI().toURL() },
                Thread.currentThread().getContextClassLoader())) {
            return BytecodeVerifier.verify(bytes, loader);
        }
    }

    // -----------------------------------------------------------------------
    // Output
    // -----------------------------------------------------------------------

    /**
     * Reconstructs the input file's path under {@code outputRoot}, preserving the
     * directory structure relative to {@code inputRoot}, and appends the mutation
     * name before the {@code .class} extension.
     *
     * <p>Example: input {@code jars/foo/Bar.class} relative to {@code inputRoot},
     * with mutation {@code ENCRYPT_TEXT}, produces
     * {@code outputRoot/jars/foo/Bar_ENCRYPT_TEXT.class}.
     *
     * <p>Path-based Javassist mutation names contain {@code /} separators (e.g.
     * {@code fsaccess/del/io}); those are flattened to {@code _} so the mutation
     * stays within a single filename component rather than creating nested
     * directories.
     */
    static Path deriveOutputPath(Path inputPath, Path inputRoot, Path outputRoot,
            String mutationName) {
        Path   relative       = inputRoot.relativize(inputPath.toAbsolutePath());
        String filename       = relative.getFileName().toString();
        String base           = filename.endsWith(".class") ? filename.replace(".class", "") : filename;
        String safeMutation   = mutationName.replace('/', '_');
        String outputFilename = base + "_" + safeMutation + ".class";
        Path   relativeDir    = relative.getParent();
        Path   outputDir      = relativeDir != null ? outputRoot.resolve(relativeDir) : outputRoot;
        return outputDir.resolve(outputFilename);
    }

    /** Writes {@code bytes} to {@code outputPath}, creating parent directories as needed. */
    static void writeOutput(Path outputPath, byte[] bytes) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, bytes);
        System.out.printf("Wrote %d bytes to %s%n", bytes.length, outputPath);
    }

    // -----------------------------------------------------------------------
    // TSV
    // -----------------------------------------------------------------------

    /** Returns the TSV header row. */
    static String buildTsvHeader() {
        return "input\toutput\tmutation\tmethod\tline\treason\tmessage";
    }

    /**
     * Applies the pre-selected {@code mutationName} to {@code classBytes},
     * writes the result to disk if verification passes, and returns a single
     * TSV row.
     *
     * <p>The row has seven tab-separated fields: input path (relative to
     * {@code inputRoot}), output path (relative to {@code outputRoot}, or empty on
     * failure), mutation name, target method name, source line number, failure
     * reason, and a free-text failure message.  The reason is empty on success,
     * {@code no_site} when the mutator finds no applicable injection point, and
     * {@code verify_failed} when ASM bytecode verification rejects the result.
     * The message holds the underlying exception/verifier text on failure and is
     * empty on success.
     *
     * @return a TSV row string, or {@code null} if the mutator cannot be resolved
     */
    static String buildTsvRow(Path inputPath, byte[] classBytes,
            Path classRoot, Path inputRoot, Path outputRoot,
            long seed, String mutationName, Map<String, JavassistMutation> snippets)
            throws IOException {
        String  input   = inputRoot.relativize(inputPath.toAbsolutePath()).toString();
        Mutator mutator = buildNamedMutator(mutationName, snippets, classRoot, seed);

        List<String> rows = new ArrayList<>();
        MutationResult result = mutator.mutateWithResult(classBytes);
        recordRow(rows, result, mutationName, inputPath, input, inputRoot, outputRoot, classRoot);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Loads snippets from {@code snippetsRoot} if the strategy includes Javassist,
     * returning an empty map otherwise.  Callers can detect a missing or unreadable
     * snippets directory immediately rather than discovering it per input file.
     */
    private static Map<String, JavassistMutation> loadSnippets(Path snippetsRoot,
            Set<MutationStrategy> strategies) throws IOException {
        if (strategies.contains(MutationStrategy.JAVASSIST)) {
            return SnippetLoader.load(snippetsRoot);
        }
        return new LinkedHashMap<>();
    }

    /**
     * Builds the ordered list of mutation names for the selected strategies.
     * Javassist names come from the pre-loaded snippets map; PIT and security
     * names come from their respective enums.
     */
    private static List<String> buildMutationNames(Map<String, JavassistMutation> snippets,
            Set<MutationStrategy> strategies) {
        List<String> names = new ArrayList<>();
        if (strategies.contains(MutationStrategy.JAVASSIST)) {
            names.addAll(snippets.keySet());
        }
        if (strategies.contains(MutationStrategy.PIT)) {
            for (PitMutations m : PitMutations.values()) names.add(m.name());
        }
        if (strategies.contains(MutationStrategy.SECURITY)) {
            for (SecurityMutations m : SecurityMutations.values()) names.add(m.name());
        }
        return names;
    }

    /**
     * Instantiates the {@link Mutator} for a single named mutation.
     *
     * <ul>
     *   <li>Javassist — looks up the pre-loaded {@link JavassistMutation} in
     *       {@code snippets} and wraps it in a {@link JavassistMutator} using
     *       {@code seed} for injection-point selection.
     *   <li>PIT — resolves the {@link PitMutations} constant and calls
     *       {@link PitMutations#mutator(Path)} with the per-file class root.
     *   <li>Security — resolves the {@link SecurityMutations} constant and calls
     *       {@link SecurityMutations#mutator(Path)} with the per-file class root.
     * </ul>
     */
    private static Mutator buildNamedMutator(String name, Map<String, JavassistMutation> snippets,
            Path classRoot, long seed) {
        JavassistMutation snippet = snippets.get(name);
        if (snippet != null) return new JavassistMutator(snippet, seed);
        try { return PitMutations.valueOf(name).mutator(classRoot); }
        catch (IllegalArgumentException ignored) {}
        return SecurityMutations.valueOf(name).mutator(classRoot);
    }

    /**
     * Verifies {@code result}, writes the mutated file on success, and appends
     * a TSV row to {@code rows}.
     *
     * <p>Every row has seven fields: input, output, mutation, method, line,
     * reason, message.  {@code reason} is empty on success; {@code no_site} when
     * the mutator found no applicable injection point; {@code verify_failed} when
     * ASM bytecode verification rejected the result.  {@code message} carries the
     * underlying exception or verifier text on failure, empty otherwise.
     */
    private static void recordRow(List<String> rows, MutationResult result,
            String mutationName, Path inputPath, String input,
            Path inputRoot, Path outputRoot, Path classRoot) throws IOException {
        String method = result.targetName() != null ? result.targetName()                 : "";
        String line   = result.lineNumber() >= 0    ? String.valueOf(result.lineNumber()) : "";
        String prefix = input + "\t\t" + mutationName + "\t" + method + "\t" + line + "\t";

        if (!result.succeeded()) {
            String detail = result.failureDetail() != null ? result.failureDetail() : "no_site";
            rows.add(prefix + detail + "\t" + sanitizeMessage(result.failureMessage()));
            return;
        }

        Optional<String> error = verifyBytecode(result.mutatedBytes(), classRoot);
        if (error.isPresent()) {
            System.out.printf("[SKIP] %s + %s: verification failed%n",
                    inputPath.getFileName(), mutationName);
            rows.add(prefix + "verify_failed\t" + sanitizeMessage(error.get()));
            return;
        }

        Path   outputPath     = deriveOutputPath(inputPath, inputRoot, outputRoot, mutationName);
        String relativeOutput = outputRoot.relativize(outputPath).toString();
        writeOutput(outputPath, result.mutatedBytes());
        rows.add(input + "\t" + relativeOutput + "\t" + mutationName + "\t" + method + "\t" + line + "\t\t");
    }

    /**
     * Flattens a failure message into a single safe TSV cell: collapses any
     * tabs/newlines to spaces and caps the length so one pathological message
     * cannot blow out the column.  Returns {@code ""} for a {@code null} message.
     */
    private static String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String oneLine = message.replaceAll("[\\t\\r\\n]+", " ").strip();
        int    cap     = 500;
        return oneLine.length() > cap ? oneLine.substring(0, cap) + "..." : oneLine;
    }

    static String formatDuration(long nanos) {

        long millis = nanos / 1_000_000;

        long seconds = millis / 1000;
        millis = millis % 1000;

        long minutes = seconds / 60;
        seconds = seconds % 60;

        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds %dms", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            return String.format("%dm %ds %dms", minutes, seconds, millis);
        } else if (seconds > 0) {
            return String.format("%ds %dms", seconds, millis);
        } else {
            return millis + "ms";
        }
    }
}
