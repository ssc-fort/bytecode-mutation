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
 *   <li>Snippets that call methods declaring checked exceptions should be wrapped
 *       with {@link #wrap(String)} so Javassist accepts them regardless of what the
 *       target method declares.</li>
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

    /**
     * Wraps {@code body} in a {@code try/catch(Throwable)} block so the
     * injected code compiles regardless of what the target method declares,
     * including methods like {@link java.lang.invoke.MethodHandle#invokeWithArguments}
     * that declare {@code throws Throwable}.
     * The caught throwable is silently discarded, which is intentional for
     * mutation payloads.
     *
     * @param body one or more Javassist-compatible statements
     * @return the body enclosed in {@code try { ... } catch (Throwable t) { }}
     */
    static String wrap(String body) {
        return "try {\n" + body + "\n} catch (Throwable t) {\n    // do nothing\n}";
    }
}
