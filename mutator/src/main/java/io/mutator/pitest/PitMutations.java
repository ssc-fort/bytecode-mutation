package io.mutator.pitest;

import io.mutator.Mutator;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator;
import org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator;

import java.nio.file.Path;

/**
 * PIT bytecode-level mutation operators integrated into the batch pipeline.
 *
 * <p>Each constant wraps a PIT {@link MethodMutatorFactory} and delegates to
 * {@link PitestMutator#mutateWithResult} to apply the first mutation site found
 * in the target class.
 *
 * <h3>Operators</h3>
 * <dl>
 *   <dt>{@link #CONDITIONALS_BOUNDARY}</dt>
 *   <dd>Shifts boundary comparisons by one: {@code <} ↔ {@code <=},
 *       {@code >} ↔ {@code >=}.</dd>
 *
 *   <dt>{@link #NEGATE_CONDITIONALS}</dt>
 *   <dd>Inverts every conditional jump:
 *       {@code ==} → {@code !=}, {@code <} → {@code >=}, etc.</dd>
 *
 *   <dt>{@link #REMOVE_CONDITIONALS_EQUAL_IF} / {@link #REMOVE_CONDITIONALS_EQUAL_ELSE}</dt>
 *   <dd>Replaces equality-based conditionals ({@code ==}, {@code !=}) so
 *       the if-branch is always taken, or always skipped.</dd>
 *
 *   <dt>{@link #REMOVE_CONDITIONALS_ORDER_IF} / {@link #REMOVE_CONDITIONALS_ORDER_ELSE}</dt>
 *   <dd>Same for order-based conditionals ({@code <}, {@code <=}, {@code >},
 *       {@code >=}).</dd>
 * </dl>
 */
public enum PitMutations {

    CONDITIONALS_BOUNDARY(
            ConditionalsBoundaryMutator.CONDITIONALS_BOUNDARY),

    NEGATE_CONDITIONALS(
            NegateConditionalsMutator.NEGATE_CONDITIONALS),

    // RemoveConditionalMutator has no public static constants — instances are
    // constructed directly. Choice.EQUAL targets == / !=; Choice.ORDER targets
    // < / <= / > / >=. The boolean selects which branch is hardwired: true = if,
    // false = else.
    REMOVE_CONDITIONALS_EQUAL_IF(
            new RemoveConditionalMutator(RemoveConditionalMutator.Choice.EQUAL, true)),

    REMOVE_CONDITIONALS_EQUAL_ELSE(
            new RemoveConditionalMutator(RemoveConditionalMutator.Choice.EQUAL, false)),

    REMOVE_CONDITIONALS_ORDER_IF(
            new RemoveConditionalMutator(RemoveConditionalMutator.Choice.ORDER, true)),

    REMOVE_CONDITIONALS_ORDER_ELSE(
            new RemoveConditionalMutator(RemoveConditionalMutator.Choice.ORDER, false));

    private final MethodMutatorFactory factory;

    PitMutations(MethodMutatorFactory factory) {
        this.factory = factory;
    }

    /**
     * Returns a {@link Mutator} configured to apply this PIT operator, using
     * {@code classRoot} to resolve sibling classes during frame computation.
     */
    public Mutator mutator(Path classRoot) {
        return new PitestMutator(factory, classRoot);
    }
}
