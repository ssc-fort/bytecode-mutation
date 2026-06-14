package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.CookieSecureSample;
import org.junit.jupiter.api.Test;

import javax.servlet.http.Cookie;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CookieSecureFlagDisableMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.CookieSecureSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(CookieSecureSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(CookieSecureFlagDisableMutator.REMOVE_SECURE_FLAG_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationRemovesSetSecure() throws Exception {
        // Original: setSecure(true) is called -> flag is set
        Cookie originalCookie = new Cookie("session", "abc");
        CookieSecureSample.secure(originalCookie);
        assertTrue(originalCookie.getSecure(), "Original: cookie should be Secure");

        // Mutated: call is removed -> flag stays at default (false)
        byte[] original = MutatorTestUtil.classBytes(CookieSecureSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(CookieSecureFlagDisableMutator.REMOVE_SECURE_FLAG_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("secure", Cookie.class);
        Cookie mutatedCookie = new Cookie("session", "abc");
        m.invoke(null, mutatedCookie);
        assertFalse(mutatedCookie.getSecure(), "Mutated: setSecure call was removed, flag should remain false");
    }
}
