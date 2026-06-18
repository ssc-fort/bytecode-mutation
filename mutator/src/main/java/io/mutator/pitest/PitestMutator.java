package io.mutator.pitest;

import io.mutator.MutationResult;
import io.mutator.Mutator;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.gregor.GregorMutater;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.reloc.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Base implementation of {@link Mutator} that drives PIT's {@link GregorMutater}
 * with a single {@link MethodMutatorFactory}.  Subclasses (or direct instantiation)
 * only need to supply the factory; all bytecode loading and mutation application
 * is handled here.
 *
 * <h3>Class resolution during frame computation</h3>
 * <p>When PIT rewrites mutated bytecode it must resolve common supertypes at every
 * stack-frame merge point.  It does so by calling back into the
 * {@link ClassByteArraySource} supplied to {@link GregorMutater}.  Three tiers of
 * resolution are attempted in order:
 * <ol>
 *   <li><b>Target class</b> — always served from the {@code byte[]} passed to
 *       {@link #mutate}.</li>
 *   <li><b>Classpath</b> — JDK classes and anything on the runtime classpath are
 *       loaded via the thread's context classloader.</li>
 *   <li><b>Class-tree root</b> — when the mutated file was loaded from the
 *       filesystem and a root was supplied via {@link #PitestMutator(MethodMutatorFactory, Path)},
 *       sibling classes (e.g. other classes from the same library JAR extracted to a
 *       directory) are looked up relative to that root.</li>
 * </ol>
 */
public class PitestMutator implements Mutator {

    private final MethodMutatorFactory factory;

    /**
     * Root of the class-file tree that contains the class being mutated.
     * {@code null} means filesystem lookup is disabled.
     * Used as tier-3 fallback for resolving sibling classes during frame
     * computation.
     */
    private final Path classRoot;

    /** Constructs a mutator with no filesystem fallback (tier 1 + tier 2 only). */
    public PitestMutator(MethodMutatorFactory factory) {
        this(factory, null);
    }

    /**
     * Constructs a mutator with a filesystem fallback for resolving sibling classes.
     *
     * @param factory   the PIT mutator to apply
     * @param classRoot root of the class-file directory tree — typically derived
     *                  via {@link #computeClassRoot(Path, byte[])}
     */
    public PitestMutator(MethodMutatorFactory factory, Path classRoot) {
        this.factory    = factory;
        this.classRoot  = classRoot;
    }

    // -----------------------------------------------------------------------
    // Mutator
    // -----------------------------------------------------------------------

    @Override
    public byte[] mutate(byte[] classBytes) {
        return mutateWithResult(classBytes).mutatedBytes();
    }

    /**
     * Like {@link #mutate(byte[])}, but also returns the method name and source
     * line of the first mutation site found, for use in TSV output.
     *
     * <p>PIT applies the first mutation site it discovers. When a class contains
     * multiple candidates (e.g. several conditionals), only the first is applied;
     * different seeds or a different ordering would be needed to reach others.
     *
     * @param classBytes raw {@code .class} file content to mutate
     * @return a {@link MutationResult} describing the outcome
     */
    public MutationResult mutateWithResult(byte[] classBytes) {
        ClassReader reader       = new ClassReader(classBytes);
        String      internalName = reader.getClassName();          // e.g. "com/example/Foo"
        String      binaryName   = internalName.replace('/', '.'); // e.g. "com.example.Foo"

        ClassByteArraySource source = name -> {
            // Names may arrive in either internal (slash) or binary (dot) form.
            String dotName = name.replace('/', '.');

            // Tier 1: the class being mutated
            if (dotName.equals(binaryName)) {
                return Optional.of(classBytes);
            }

            // Tier 2: JDK classes and anything else on the runtime classpath
            Optional<byte[]> fromClasspath = loadFromClasspath(dotName);
            if (fromClasspath.isPresent()) {
                return fromClasspath;
            }

            // Tier 3: sibling classes in the same on-disk class tree
            return loadFromClassTree(dotName);
        };

        GregorMutater mutater = new GregorMutater(
                source, info -> true, Collections.singletonList(factory));

        List<MutationDetails> mutations =
                mutater.findMutations(ClassName.fromString(binaryName));

        if (mutations.isEmpty()) {
            System.out.println("No '" + factory.getName()
                    + "' mutations found in " + binaryName);
            return new MutationResult(classBytes, false, null, -1, "no_site", null);
        }

        MutationDetails first      = mutations.get(0);
        System.out.printf("Applying mutation '%s' in %s at line %d%n",
                first.getDescription(), first.getMethod(), first.getLineNumber());

        Mutant mutant     = mutater.getMutation(first.getId());
        String methodName = first.getMethod();
        int    lineNumber = first.getLineNumber();
        return new MutationResult(mutant.getBytes(), true, methodName, lineNumber, null, null);
    }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    /**
     * Derives the root of a class-file directory tree from the path of one
     * {@code .class} file within it.
     *
     * <p>The internal class name (read from the bytecode) encodes the package
     * depth — e.g. {@code com/example/foo/Bar} is 4 levels deep, so the root
     * is 4 parent directories above the {@code .class} file.
     *
     * <p>Example:
     * <pre>
     *   file  : /libs/checkstyle/com/puppycrawl/tools/checkstyle/Checker.class
     *   name  : com/puppycrawl/tools/checkstyle/Checker   (5 segments)
     *   root  : /libs/checkstyle/
     * </pre>
     *
     * @param classFilePath absolute or relative path to the {@code .class} file
     * @param classBytes    raw bytes of that file (used to read the internal name)
     * @return the directory that is the root of the class tree
     */
    public static Path computeClassRoot(Path classFilePath, byte[] classBytes) {
        ClassReader cr    = new ClassReader(classBytes);
        int         depth = cr.getClassName().split("/").length;
        Path        root  = classFilePath.toAbsolutePath();
        for (int i = 0; i < depth; i++) {
            root = root.getParent();
        }
        return root;
    }

    // -----------------------------------------------------------------------
    // Private resolution helpers
    // -----------------------------------------------------------------------

    /**
     * Tier-2: loads class bytes from the thread's context classloader (or the
     * system classloader if no context loader is set).  Finds JDK classes and
     * anything on the runtime classpath.
     */
    private static Optional<byte[]> loadFromClasspath(String binaryName) {
        String      resourcePath = binaryName.replace('.', '/') + ".class";
        ClassLoader cl           = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return Optional.of(is.readAllBytes());
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    /**
     * Tier-3: looks up a class file relative to {@link #classRoot}.
     * Used for classes that live alongside the mutated class in the same
     * on-disk directory tree but are not on the tool's runtime classpath.
     */
    private Optional<byte[]> loadFromClassTree(String binaryName) {
        if (classRoot == null) {
            return Optional.empty();
        }
        // binaryName may be in dot or slash form — normalise to path segments
        String[] segments = binaryName.replace('/', '.').split("\\.");
        Path classFile = classRoot;
        for (String segment : segments) {
            classFile = classFile.resolve(segment);
        }
        classFile = classFile.resolveSibling(segments[segments.length - 1] + ".class");
        try {
            if (Files.exists(classFile)) {
                return Optional.of(Files.readAllBytes(classFile));
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }
}
