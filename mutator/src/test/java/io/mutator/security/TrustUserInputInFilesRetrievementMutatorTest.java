package io.mutator.security;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.FilenameSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TrustUserInputInFilesRetrievementMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.FilenameSample";

    @Test
    void mutationChangesBytes() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(FilenameSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(TrustUserInputInFilesRetrievementMutator.TRUST_USER_INPUT_IN_FILES_RETRIEVEMENT, original);
        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void mutationReturnRawPathInsteadOfFilename() throws Exception {
        String traversalPath = "../../etc/passwd";

        // Original: FilenameUtils.getName strips the path components
        assertEquals("passwd", FilenameSample.safeName(traversalPath),
                "Original: should return only the filename component");

        // Mutated: FilenameUtils.getName call is removed, raw input is returned as-is
        byte[] original = MutatorTestUtil.classBytes(FilenameSample.class);
        byte[] mutated  = MutatorTestUtil.mutate(TrustUserInputInFilesRetrievementMutator.TRUST_USER_INPUT_IN_FILES_RETRIEVEMENT, original);
        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("safeName", String.class);
        String result = (String) m.invoke(null, traversalPath);
        assertEquals(traversalPath, result, "Mutated: raw path should be returned unchanged");
    }
}
