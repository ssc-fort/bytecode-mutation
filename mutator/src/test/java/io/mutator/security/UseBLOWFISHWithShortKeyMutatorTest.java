package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.BlowfishSample;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class UseBLOWFISHWithShortKeyMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.BlowfishSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(BlowfishSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseBLOWFISHWithShortKeyMutator.USE_BLOWFISH_WITH_SHORT_KEY, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReducesKeyTo64Bits() throws Exception {
        // Mutated: init(256) replaced with init(64) -> key should be 8 bytes
        byte[] original = MutatorTestUtil.classBytes(BlowfishSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(UseBLOWFISHWithShortKeyMutator.USE_BLOWFISH_WITH_SHORT_KEY, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("generateKey");
        SecretKey key = (SecretKey) m.invoke(null);
        assertEquals(8, key.getEncoded().length,
                "Mutated Blowfish key should be 64 bits (8 bytes) instead of 256 bits");
    }
}
