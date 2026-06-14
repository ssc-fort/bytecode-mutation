package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.CookieHttpOnlySample;
import org.junit.jupiter.api.Test;

import javax.servlet.http.Cookie;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CookieHttpOnlyFlagDisableMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.CookieHttpOnlySample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(CookieHttpOnlySample.class);
        byte[] mutated  = MutatorTestUtil.mutate(CookieHttpOnlyFlagDisableMutator.REMOVE_HTTPONLY_FLAG_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationRemovesSetHttpOnly() throws Exception {
        // Original: setHttpOnly(true) is called -> flag is set
        Cookie originalCookie = new Cookie("session", "abc");
        CookieHttpOnlySample.secure(originalCookie);
        assertTrue(originalCookie.isHttpOnly(), "Original: cookie should be HttpOnly");

        // Mutated: call is removed -> flag stays at default (false)
        byte[] original = MutatorTestUtil.classBytes(CookieHttpOnlySample.class);
        byte[] mutated  = MutatorTestUtil.mutate(CookieHttpOnlyFlagDisableMutator.REMOVE_HTTPONLY_FLAG_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("secure", Cookie.class);
        Cookie mutatedCookie = new Cookie("session", "abc");
        m.invoke(null, mutatedCookie);
        assertFalse(mutatedCookie.isHttpOnly(), "Mutated: setHttpOnly call was removed, flag should remain false");
    }
}
