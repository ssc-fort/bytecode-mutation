package io.mutator.javassist;

/**
 * A Javassist source-code fragment to inject into a class.
 *
 * <p>Captures only <em>what</em> code to inject. <em>Where</em> (method/constructor)
 * and <em>when</em> (BEFORE/AFTER) are chosen by {@link JavassistMutator}, which
 * discovers every injectable point and selects one deterministically using a seed.
 *
 * <p>Use with {@link JavassistMutator#JavassistMutator(JavassistMutation, long)}.
 *
 * <h3>Javassist source-code constraints</h3>
 * <p>Javassist uses a Java-5-era parser. Keep these rules in mind when writing
 * snippets:
 * <ul>
 *   <li>{@code var} is not supported — use explicit types.</li>
 *   <li>Static interface methods (e.g. {@code Path.of}, {@code List.of}) are not
 *       resolved — use equivalent utility classes ({@code new File(...).toPath()}).</li>
 *   <li>Varargs calls (e.g. {@code Files.write(...)}) are not matched by a
 *       single-arg call — use a non-varargs alternative (e.g. {@code FileOutputStream}).</li>
 *   <li>Generic wildcards in local variable declarations are not supported —
 *       use raw types ({@code Class} instead of {@code Class<?>}).</li>
 *   <li>Exception handling is the snippet's own responsibility: code is injected
 *       verbatim, so a snippet that calls methods declaring checked exceptions
 *       must include its own {@code try/catch} (e.g. {@code catch (Throwable t)}),
 *       otherwise Javassist will reject it for any target method that does not
 *       declare those exceptions.</li>
 * </ul>
 */
@FunctionalInterface
public interface JavassistMutation {

    /**
     * Javassist-compatible Java source fragment to inject.
     * May reference {@code $0} (this), {@code $1}/{@code $2}/… (parameters),
     * {@code $args} (all parameters as {@code Object[]}), and {@code $_}
     * (return value, in AFTER context only).
     */
    String sourceCode();

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    /** Creates a {@link JavassistMutation} from a raw source fragment. */
    static JavassistMutation of(String sourceCode) {
        return () -> sourceCode;
    }
}
