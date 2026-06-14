package io.mutator;

import io.mutator.pitest.PitestMutator;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public final class MutatorTestUtil {

    private MutatorTestUtil() {}

    /** Loads raw .class bytes for the given class from the test classpath. */
    public static byte[] classBytes(Class<?> clazz) throws Exception {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (var stream = MutatorTestUtil.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Class resource not found: " + resource);
            return stream.readAllBytes();
        }
    }

    /** Applies the given mutator to classBytes via PitestMutator. */
    public static byte[] mutate(MethodMutatorFactory factory, byte[] classBytes) {
        return new PitestMutator(factory).mutate(classBytes);
    }

    /** Asserts the byte arrays differ (i.e. a mutation was actually applied). */
    public static void assertBytecodeChanged(byte[] original, byte[] mutated) {
        assertFalse(Arrays.equals(original, mutated),
                "Expected mutated bytecode to differ from original");
    }

    /**
     * Asserts the mutated bytecode passes ASM's structural + data-flow verification.
     * Fails the test with the verification error message if it does not.
     */
    public static void assertBytecodeValid(byte[] bytes) {
        Optional<String> error = BytecodeVerifier.verify(bytes);
        assertTrue(error.isEmpty(), () -> "Bytecode verification failed:\n" + error.get());
    }

    /**
     * Loads class bytes into an isolated child classloader so the class can be
     * loaded under the same binary name even if it is already on the classpath.
     */
    public static Class<?> loadFromBytes(String binaryName, byte[] bytes)
            throws ClassNotFoundException {
        ClassLoader loader = new ClassLoader(
                Thread.currentThread().getContextClassLoader()) {
            @Override
            public Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.equals(binaryName)) {
                    Class<?> c = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(c);
                    return c;
                }
                return super.loadClass(name, resolve);
            }
        };
        return loader.loadClass(binaryName);
    }
}
