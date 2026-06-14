package io.mutator;

import java.util.Arrays;

/**
 * Common interface for all bytecode mutators.
 *
 * <p>Implementors must provide {@link #mutate}. The default
 * {@link #mutateWithResult} infers success by comparing bytes; richer
 * implementations ({@link io.mutator.javassist.JavassistMutator},
 * {@link io.mutator.pitest.PitestMutator}) override it to also return the
 * target method name and source line number.
 */
public interface Mutator {

    /** Returns mutated class bytes, or the original bytes if no mutation was applied. */
    byte[] mutate(byte[] classBytes);

    /**
     * Applies the mutation and returns a {@link MutationResult} describing the
     * outcome.  The default implementation calls {@link #mutate} and infers
     * success by comparing the returned bytes to the input.
     */
    default MutationResult mutateWithResult(byte[] classBytes) {
        byte[] mutated    = mutate(classBytes);
        boolean succeeded = !Arrays.equals(mutated, classBytes);
        return new MutationResult(mutated, succeeded, null, -1, succeeded ? null : "no_site");
    }
}
