package io.mutator;

/**
 * The outcome of a single mutation attempt, returned by
 * {@link io.mutator.javassist.JavassistMutator#mutateWithResult} and
 * {@link io.mutator.pitest.PitestMutator#mutateWithResult}.
 *
 * @param mutatedBytes  resulting class bytes; identical to the input if
 *                      {@code succeeded} is {@code false}
 * @param succeeded     {@code true} if the mutation was applied;
 *                      {@code false} if no applicable site was found or the
 *                      mutation could not be applied
 * @param targetName    name of the method or constructor that was targeted,
 *                      or {@code null} if no target was found
 * @param lineNumber    source line of the mutation site, or {@code -1} if
 *                      debug info is absent or no target was found
 * @param failureDetail machine-readable reason string when {@code succeeded}
 *                      is {@code false}; {@code null} on success.
 *                      Format is {@code category} or {@code category:sub},
 *                      e.g. {@code no_site:interface}, {@code inject_failed:compile}.
 *                      See {@link io.mutator.javassist.JavassistMutator} for
 *                      the full set of values it produces.
 * @param failureMessage human-readable detail for the failure — typically the
 *                      throwing exception's type and message — or {@code null}
 *                      when there is none (success, or a no-site outcome with no
 *                      underlying exception).
 */
public record MutationResult(
        byte[]  mutatedBytes,
        boolean succeeded,
        String  targetName,
        int     lineNumber,
        String  failureDetail,
        String  failureMessage) {}
