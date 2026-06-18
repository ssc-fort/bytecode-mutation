package io.mutator.javassist;

import io.mutator.Mutator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>Each {@code .jsnippet} file contains the raw Java body to inject and is
 * loaded verbatim — the loader does not add any exception handling.  Whether to
 * catch exceptions is the snippet's own decision: a snippet that calls methods
 * declaring checked exceptions must include its own {@code try/catch}, otherwise
 * Javassist will reject it for any target method that does not declare them.
 *
 * <p>Snippet bodies may contain {@link SnippetToken} placeholders of the form
 * {@code #TOKEN_NAME#}.  These are <em>not</em> resolved here: substitution is
 * deferred to {@link JavassistMutator} so that, at injection time, string tokens
 * can additionally draw from the target behavior's own {@code String}
 * parameters.  The loaded {@link JavassistMutation} therefore carries the raw
 * (wrapped) template with its tokens still present.
 *
 * <p>The mutation name exposed to callers is the snippet's path relative to
 * {@code snippetsRoot}, using {@code /} separators and without the
 * {@code .jsnippet} extension (e.g. {@code crypto/decrypt/chacha20} for
 * {@code <root>/crypto/decrypt/chacha20.jsnippet}).  Using the full relative
 * path keeps snippets that share a filename distinct (e.g.
 * {@code fsaccess/del/io} vs {@code fsaccess/readsecret/io}).  Files are returned
 * in sorted order for consistent, reproducible iteration.
 */
public class SnippetLoader {

    private SnippetLoader() {}

    /**
     * Walks {@code snippetsRoot} recursively and returns an ordered map of
     * {@code name → JavassistMutation}.  Each mutation's source is the file body
     * verbatim, with {@link SnippetToken} placeholders left intact for
     * injection-time substitution.
     *
     * @param snippetsRoot root of the snippet directory tree
     * @return insertion-ordered map; empty if the directory contains no snippets
     * @throws IOException if the directory cannot be walked or a file cannot be read
     */
    public static Map<String, JavassistMutation> load(Path snippetsRoot) throws IOException {
        Map<String, JavassistMutation> result = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(snippetsRoot)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".jsnippet"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         String body = Files.readString(p).strip();
                         String name = relativeName(snippetsRoot, p);
                         result.put(name, JavassistMutation.of(body));
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
     * Builds an ordered map of {@code name → Mutator} ready for use in the batch
     * pipeline.  The seed is forwarded to each {@link JavassistMutator}, which
     * uses it for both injection-point selection and token substitution, so the
     * same seed always produces identical mutations end-to-end.
     *
     * @param snippetsRoot root of the snippet directory tree
     * @param seed         seed for injection-point selection and token substitution
     * @return insertion-ordered map of configured mutators
     * @throws IOException if snippets cannot be loaded
     */
    public static Map<String, Mutator> loadMutators(Path snippetsRoot, long seed)
            throws IOException {
        Map<String, Mutator> mutators = new LinkedHashMap<>();
        for (Map.Entry<String, JavassistMutation> e : load(snippetsRoot).entrySet()) {
            mutators.put(e.getKey(), new JavassistMutator(e.getValue(), seed));
        }
        return mutators;
    }

    /**
     * Returns {@code file}'s path relative to {@code root}, normalised to
     * {@code /} separators and with the {@code .jsnippet} extension removed —
     * e.g. {@code crypto/decrypt/chacha20}.
     */
    private static String relativeName(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".jsnippet".length());
    }
}
