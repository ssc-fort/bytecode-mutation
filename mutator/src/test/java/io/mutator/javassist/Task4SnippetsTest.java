package io.mutator.javassist;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.JavassistSample;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end validation of the typed {@code @in} placeholder engine against the
 * real security snippet tree (the freshly-checked-out task4 snippets). For each
 * snippet it tries every injection target in {@link JavassistSample} and checks
 * that at least one produces changed, structurally-valid bytecode — i.e. the
 * placeholders substitute into compilable code. Failures are printed for triage.
 */
class Task4SnippetsTest {

    private static final Path TASK4_SNIPPETS =
            Path.of("..", "task4-ai4bineq", "sec-mutations", "snippets");

    @Test
    void everySnippetInjectsIntoAtLeastOneTarget() throws Exception {
        Map<String, JavassistMutation> snippets = SnippetLoader.load(TASK4_SNIPPETS);
        assertTrue(snippets.size() >= 40, "expected the full task4 snippet tree, found " + snippets.size());

        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        int targetCount = JavassistMutator.discoverTargets(original).size();

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, JavassistMutation> e : snippets.entrySet()) {
            boolean ok = false;
            for (int seed = 0; seed < targetCount && !ok; seed++) {
                byte[] mutated = new JavassistMutator(e.getValue(), seed).mutate(original);
                if (!Arrays.equals(original, mutated)) {
                    MutatorTestUtil.assertBytecodeValid(mutated);
                    ok = true;
                }
            }
            if (!ok) {
                failures.add(e.getKey());
            }
        }

        if (!failures.isEmpty()) {
            System.out.println("Task4 snippets that injected into NO target (" + failures.size()
                    + "/" + snippets.size() + "):");
            failures.forEach(f -> System.out.println("  " + f));
        }
        assertTrue(failures.isEmpty(),
                "snippets failed to inject into any target: " + failures);
    }
}
