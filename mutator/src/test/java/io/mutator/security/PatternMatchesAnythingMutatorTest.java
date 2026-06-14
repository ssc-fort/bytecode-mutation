package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.PatternSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PatternMatchesAnythingMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.PatternSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(PatternSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(PatternMatchesAnythingMutator.PATTERN_MATCHES_ANYTHING_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationMakesPatternMatchAnything() throws Exception {
        // Original: only digits match
        assertFalse(PatternSample.matchesDigitsOnly("abc"), "Original: 'abc' should not match digits-only pattern");
        assertTrue(PatternSample.matchesDigitsOnly("123"), "Original: '123' should match digits-only pattern");

        // Mutated: regex replaced with ([^¤]*) which matches any string without ¤
        byte[] original = MutatorTestUtil.classBytes(PatternSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(PatternMatchesAnythingMutator.PATTERN_MATCHES_ANYTHING_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("matchesDigitsOnly", String.class);
        boolean result = (Boolean) m.invoke(null, "abc");
        assertTrue(result, "Mutated: 'abc' should match the weakened pattern");
    }
}
