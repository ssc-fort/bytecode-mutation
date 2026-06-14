package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.MessageDigestSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class UseMD5ForEncryptionJAVAStandardMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.MessageDigestSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(MessageDigestSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseMD5ForEncryptionJAVAStandardMutator.USE_MD5_FOR_ENCRYPTION_JAVA_STANDARD_MUTATOR, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReplacesSHA256WithMD5() throws Exception {
        // Original: SHA-256
        assertEquals("SHA-256", MessageDigestSample.getDigest().getAlgorithm(),
                "Original: algorithm should be SHA-256");

        // Mutated: MD5
        byte[] original = MutatorTestUtil.classBytes(MessageDigestSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseMD5ForEncryptionJAVAStandardMutator.USE_MD5_FOR_ENCRYPTION_JAVA_STANDARD_MUTATOR, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("getDigest");
        MessageDigest digest = (MessageDigest) m.invoke(null);
        assertEquals("MD5", digest.getAlgorithm(), "Mutated: algorithm should be MD5");
    }
}
