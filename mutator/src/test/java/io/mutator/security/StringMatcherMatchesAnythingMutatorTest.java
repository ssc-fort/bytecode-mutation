package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.StringMatcherSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class StringMatcherMatchesAnythingMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.StringMatcherSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(StringMatcherSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(StringMatcherMatchesAnythingMutator.STRING_MATCHER_MATCHES_ANYTHING_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationMakesStringMatchAnything() throws Exception {
        // Original: only digits match
        assertFalse(StringMatcherSample.matchesDigitsOnly("abc"), "Original: 'abc' should not match digits-only pattern");
        assertTrue(StringMatcherSample.matchesDigitsOnly("123"), "Original: '123' should match digits-only pattern");

        // Mutated: regex replaced with ([^¤]*) which matches any string without ¤
        byte[] original = MutatorTestUtil.classBytes(StringMatcherSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(StringMatcherMatchesAnythingMutator.STRING_MATCHER_MATCHES_ANYTHING_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("matchesDigitsOnly", String.class);
        boolean result = (Boolean) m.invoke(null, "abc");
        assertTrue(result, "Mutated: 'abc' should match the weakened pattern");
    }
}
