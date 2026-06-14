package io.mutator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Verifies that a byte array represents structurally and data-flow-correct JVM bytecode.
 *
 * <p>Uses ASM's {@link CheckClassAdapter#verify} which performs two passes:
 * <ol>
 *   <li>Structural checks (valid constant-pool references, opcode constraints, etc.)</li>
 *   <li>Data-flow analysis on every method (stack heights, type compatibility)</li>
 * </ol>
 *
 * <h3>Type resolution during data-flow analysis</h3>
 * <p>The data-flow pass uses {@link org.objectweb.asm.tree.analysis.SimpleVerifier},
 * which needs to load every class referenced in the bytecode to check subtype
 * relationships at merge points.  When a class cannot be loaded (e.g. because it
 * belongs to a third-party library not on the tool's own classpath) the analyser
 * reports an error, even though the bytecode itself may be perfectly valid.
 *
 * <p>Pass a {@link ClassLoader} that can resolve the library's types to
 * {@link #verify(byte[], ClassLoader)} to avoid spurious failures.
 * The no-loader overload {@link #verify(byte[])} uses the bootstrap/system loader,
 * which is appropriate when the class being verified only references JDK types.
 *
 * <p>{@link Optional#empty()} is returned when the bytes are valid; a non-empty
 * {@code Optional<String>} contains the failure description when they are not.
 */
public class BytecodeVerifier {

    private BytecodeVerifier() {}

    /**
     * Verifies {@code classBytes} using the system classloader for type resolution.
     * Suitable for classes whose dependencies are all JDK or otherwise on the
     * runtime classpath.
     *
     * @param classBytes raw {@code .class} file content
     * @return {@link Optional#empty()} on success, or an {@code Optional} containing
     *         the verification failure message on failure
     */
    public static Optional<String> verify(byte[] classBytes) {
        return verify(classBytes, null);
    }

    /**
     * Verifies {@code classBytes} using {@code loader} for type resolution during
     * the data-flow analysis pass.
     *
     * <p>Use this overload when the class references types from a library that is
     * not on the tool's own runtime classpath — for example, when mutating a class
     * extracted from a third-party JAR.  Provide a {@link ClassLoader} that can
     * find those types (e.g. a {@link java.net.URLClassLoader} pointing at the
     * library's class-tree root).
     *
     * <p>Passing {@code null} for {@code loader} is equivalent to calling
     * {@link #verify(byte[])}.
     *
     * @param classBytes raw {@code .class} file content
     * @param loader     classloader used to resolve referenced types, or {@code null}
     *                   to use the bootstrap/system classloader
     * @return {@link Optional#empty()} on success, or an {@code Optional} containing
     *         the verification failure message on failure
     */
    public static Optional<String> verify(byte[] classBytes, ClassLoader loader) {
        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);

        try {
            ClassReader cr = new ClassReader(classBytes);
            // dump = false: only print errors, not every instruction
            CheckClassAdapter.verify(cr, loader, false, pw);
        } catch (Throwable e) {
            // Catch Error as well as Exception: ASM's SimpleVerifier throws
            // NoClassDefFoundError when a referenced type (e.g. a test framework
            // base class) cannot be loaded by the supplied ClassLoader.
            pw.println("Structural error: " + e.getMessage());
        }

        pw.flush();
        String output = sw.toString().trim();
        return output.isEmpty() ? Optional.empty() : Optional.of(output);
    }
}
