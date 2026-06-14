package io.mutator.javassist;

import io.mutator.Mutator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Loads Javassist mutations from a directory tree of {@code .jsnippet} files.
 *
 * <p>The expected layout is:
 * <pre>
 *   snippets/
 *     &lt;type&gt;/
 *       &lt;subtype&gt;/
 *         &lt;name&gt;.jsnippet
 * </pre>
 *
 * <p>Each {@code .jsnippet} file contains the raw Java body to inject —
 * statements only, without a surrounding {@code try/catch}.  The loader
 * applies {@link JavassistMutation#wrap} automatically so that checked
 * exceptions in the body do not affect compilability.
 *
 * <p>Snippet bodies may contain {@link SnippetToken} placeholders of the form
 * {@code #TOKEN_NAME#}.  These are substituted with seeded-random candidates
 * before the body is compiled, so the same seed always produces the same
 * injected code.  Call {@link #load(Path, long)} or {@link #loadMutators} to
 * enable substitution; the no-seed {@link #load(Path)} overload uses seed
 * {@code 0} and is intended for tests.
 *
 * <p>The mutation name exposed to callers is the filename stem (e.g.
 * {@code decryptText} for {@code aes/decryptText.jsnippet}).  Files are
 * returned in sorted order for consistent, reproducible iteration.
 */
public class SnippetLoader {

    private SnippetLoader() {}

    /**
     * Loads snippets using seed {@code 0} for token substitution.
     * Intended for tests; use {@link #load(Path, long)} for production.
     *
     * @param snippetsRoot root of the snippet directory tree
     * @return insertion-ordered map; empty if the directory contains no snippets
     * @throws IOException if the directory cannot be walked or a file cannot be read
     */
    public static Map<String, JavassistMutation> load(Path snippetsRoot) throws IOException {
        return load(snippetsRoot, 0L);
    }

    /**
     * Walks {@code snippetsRoot} recursively, substitutes {@link SnippetToken}
     * placeholders using {@code seed}, and returns an ordered map of
     * {@code name → JavassistMutation}.
     *
     * <p>A single {@link Random} seeded with {@code seed} is advanced once per
     * token occurrence across all snippets in sorted file order, so the same
     * seed always produces identical substitutions.
     *
     * @param snippetsRoot root of the snippet directory tree
     * @param seed         seed for token substitution randomness
     * @return insertion-ordered map; empty if the directory contains no snippets
     * @throws IOException if the directory cannot be walked or a file cannot be read
     */
    public static Map<String, JavassistMutation> load(Path snippetsRoot, long seed)
            throws IOException {
        Map<String, JavassistMutation> result = new LinkedHashMap<>();
        Random rng = new Random(seed);

        try (Stream<Path> paths = Files.walk(snippetsRoot)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".jsnippet"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         String body       = Files.readString(p).strip();
                         String name       = stem(p);
                         String resolved   = SnippetToken.substituteAll(body, rng);
                         String sourceCode = JavassistMutation.wrap(resolved);
                         result.put(name, JavassistMutation.of(sourceCode));
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        return result;
    }

    /**
     * Builds an ordered map of {@code name → Mutator} ready for use in the
     * batch pipeline.  The seed is used for both token substitution and
     * injection-point selection, so the same seed always produces identical
     * mutations end-to-end.
     *
     * @param snippetsRoot root of the snippet directory tree
     * @param seed         seed for token substitution and injection-point selection
     * @return insertion-ordered map of configured mutators
     * @throws IOException if snippets cannot be loaded
     */
    public static Map<String, Mutator> loadMutators(Path snippetsRoot, long seed)
            throws IOException {
        Map<String, Mutator> mutators = new LinkedHashMap<>();
        for (Map.Entry<String, JavassistMutation> e : load(snippetsRoot, seed).entrySet()) {
            mutators.put(e.getKey(), new JavassistMutator(e.getValue(), seed));
        }
        return mutators;
    }

    /** Returns the filename without its {@code .jsnippet} extension. */
    private static String stem(Path path) {
        String filename = path.getFileName().toString();
        return filename.substring(0, filename.length() - ".jsnippet".length());
    }
}
