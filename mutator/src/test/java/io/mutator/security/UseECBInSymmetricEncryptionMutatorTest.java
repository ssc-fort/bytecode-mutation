package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.CipherSample;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class UseECBInSymmetricEncryptionMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.CipherSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(CipherSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseECBInSymmetricEncryptionMutator.USE_ECB_IN_SYMMETRIC_ENCRYPTION, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReplacesModewithECB() throws Exception {
        // Original: AES/CBC/PKCS5Padding
        assertEquals("AES/CBC/PKCS5Padding", CipherSample.getCipher().getAlgorithm(),
                "Original: cipher algorithm should be AES/CBC/PKCS5Padding");

        // Mutated: AES/ECB/PKCS5Padding
        byte[] original = MutatorTestUtil.classBytes(CipherSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseECBInSymmetricEncryptionMutator.USE_ECB_IN_SYMMETRIC_ENCRYPTION, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("getCipher");
        Cipher cipher = (Cipher) m.invoke(null);
        assertEquals("AES/ECB/PKCS5Padding", cipher.getAlgorithm(),
                "Mutated: cipher algorithm should be AES/ECB/PKCS5Padding");
    }
}
