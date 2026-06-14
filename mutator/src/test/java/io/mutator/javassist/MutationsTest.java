package io.mutator.javassist;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.JavassistSample;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates every snippet in the {@code snippets/} directory tree.
 *
 * <p>For each snippet the test iterates over every injectable target in
 * {@link JavassistSample} (one seed per target) and asserts that at least one
 * injection succeeds — i.e. produces changed, structurally-valid bytecode.
 * This catches broken source fragments (bad syntax, unsupported API calls,
 * wrong types) before a long batch run discovers them at scale.
 */
class MutationsTest {

    private static final Path SNIPPETS_ROOT = Path.of("snippets");

    static Stream<Map.Entry<String, JavassistMutation>> snippets() throws IOException {
        return SnippetLoader.load(SNIPPETS_ROOT).entrySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snippets")
    void eachSnippet_injectsSuccessfullyIntoAtLeastOneTarget(
            Map.Entry<String, JavassistMutation> entry) throws Exception {

        String            name     = entry.getKey();
        JavassistMutation mutation = entry.getValue();

        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);

        boolean anySuccess = false;
        for (int seed = 0; seed < targets.size(); seed++) {
            byte[] mutated = new JavassistMutator(mutation, seed).mutate(original);
            if (!Arrays.equals(original, mutated)) {
                MutatorTestUtil.assertBytecodeValid(mutated);
                anySuccess = true;
                break;
            }
        }

        assertTrue(anySuccess,
                "Snippet '" + name + "' could not be injected into any target in JavassistSample");
    }
}
