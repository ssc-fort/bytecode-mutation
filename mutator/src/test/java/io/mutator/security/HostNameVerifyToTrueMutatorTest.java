package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.HostnameVerifierSample;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HostNameVerifyToTrueMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.HostnameVerifierSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(HostnameVerifierSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(HostNameVerifyToTrueMutator.HOST_NAME_VERIFY_TO_TRUE, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReplacesVerifyWithTrue() throws Exception {
        HostnameVerifier alwaysFalse = (hostname, session) -> false;

        // Original: delegates to verifier -> false
        assertFalse(HostnameVerifierSample.callVerify(alwaysFalse, "example.com", null));

        // Mutated: ignores verifier, always returns true
        byte[] original = MutatorTestUtil.classBytes(HostnameVerifierSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(HostNameVerifyToTrueMutator.HOST_NAME_VERIFY_TO_TRUE, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("callVerify", HostnameVerifier.class, String.class, SSLSession.class);
        boolean result = (Boolean) m.invoke(null, alwaysFalse, "example.com", (SSLSession) null);
        assertTrue(result, "Mutated verify() should unconditionally return true");
    }
}
