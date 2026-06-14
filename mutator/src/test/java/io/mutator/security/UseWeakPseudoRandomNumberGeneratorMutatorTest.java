package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.SecureRandomSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class UseWeakPseudoRandomNumberGeneratorMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.SecureRandomSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(SecureRandomSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseWeakPseudoRandomNumberGeneratorMutator.USE_WEAK_PSEUDO_RANDOM_NUMBER_GENERATOR_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReplacesSecureRandomWithRandom() throws Exception {
        // Mutated: SecureRandom.nextBytes replaced with Random.nextBytes
        // The method should still produce a filled byte array of the correct length
        byte[] original = MutatorTestUtil.classBytes(SecureRandomSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseWeakPseudoRandomNumberGeneratorMutator.USE_WEAK_PSEUDO_RANDOM_NUMBER_GENERATOR_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("generate", SecureRandom.class, int.class);
        SecureRandom sr = new SecureRandom();
        byte[] result = (byte[]) m.invoke(null, sr, 16);
        assertNotNull(result, "Mutated: result should not be null");
        assertEquals(16, result.length, "Mutated: result should have the requested length");
    }
}
