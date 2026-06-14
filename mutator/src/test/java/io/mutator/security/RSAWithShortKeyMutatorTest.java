package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.RSASample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;

import static org.junit.jupiter.api.Assertions.*;

class RSAWithShortKeyMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.RSASample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(RSASample.class);
        byte[] mutated  = MutatorTestUtil.mutate(RSAWithShortKeyMutator.RSA_WITH_SHORT_KEY_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReducesKeyTo512Bits() throws Exception {
        // Mutated: initialize(2048) replaced with initialize(512)
        byte[] original = MutatorTestUtil.classBytes(RSASample.class);
        byte[] mutated  = MutatorTestUtil.mutate(RSAWithShortKeyMutator.RSA_WITH_SHORT_KEY_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("generateKeyPair");
        KeyPair kp = (KeyPair) m.invoke(null);
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        assertEquals(512, priv.getModulus().bitLength(),
                "Mutated RSA should use 512-bit key instead of 2048-bit");
    }
}
