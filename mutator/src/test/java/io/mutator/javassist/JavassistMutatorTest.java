package io.mutator.javassist;

import io.mutator.MutatorTestUtil;
import io.mutator.samples.JavassistSample;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavassistMutatorTest {

    private static final String SAMPLE = "io.mutator.samples.JavassistSample";

    // -----------------------------------------------------------------------
    // Bytecode-level assertions (no classloading needed)
    // -----------------------------------------------------------------------

    @Test
    void insertBefore_default_changesBytecodeAndIsValid() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        // Default constructor uses InsertionPoint.BEFORE
        byte[] mutated = new JavassistMutator("compute",
                "System.out.println($1);").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void insertAfter_changesBytecodeAndIsValid() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated = new JavassistMutator("compute",
                "System.out.println($_);",
                JavassistMutator.InsertionPoint.AFTER).mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    // -----------------------------------------------------------------------
    // Behavioural tests: insertBefore
    // -----------------------------------------------------------------------

    /**
     * Injects a guard clause that references parameter $1 (an int).
     * Values above 100 short-circuit to -1; the normal path is unaffected.
     */
    @Test
    void insertBefore_intParameterReference_addsGuardClause() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated = new JavassistMutator("compute",
                "if ($1 > 100) return -1;").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("compute", int.class);

        // Guard fires: 200 + 10 would be 210, but injection returns -1 first
        assertEquals(-1, (int) m.invoke(null, 200),
                "Injected guard clause should short-circuit for x > 100");

        // Normal path: guard does not fire, original logic runs
        assertEquals(15, (int) m.invoke(null, 5),
                "Normal path (x <= 100) should be unchanged");
    }

    /**
     * Injects a null guard that references parameter $1 (a String reference).
     * A null argument is intercepted and a safe default is returned instead.
     */
    @Test
    void insertBefore_stringParameterReference_addsNullGuard() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated = new JavassistMutator("greet",
                "if ($1 == null) return \"unknown\";").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("greet", String.class);

        // Null is intercepted by the injected guard
        assertEquals("unknown", (String) m.invoke(null, (Object) null),
                "Injected null guard should return 'unknown' for a null argument");

        // Non-null input should reach the original method body unchanged
        assertEquals("Hello, World", (String) m.invoke(null, "World"),
                "Non-null input should not be affected by the guard");
    }

    // -----------------------------------------------------------------------
    // Behavioural tests: insertAfter / $_ return-value modification
    // -----------------------------------------------------------------------

    /**
     * Uses insertAfter with $_ to double the return value.
     * Original: getValue() → 42.  Mutated: getValue() → 84.
     */
    @Test
    void insertAfter_returnValueModification() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated = new JavassistMutator("getValue",
                "$_ = $_ * 2;",
                JavassistMutator.InsertionPoint.AFTER).mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        int result = (int) mutatedClass.getMethod("getValue").invoke(null);
        assertEquals(84, result, "Injected code should double the return value (42 → 84)");
    }

    // -----------------------------------------------------------------------
    // Instance methods
    // -----------------------------------------------------------------------

    /**
     * Verifies that $1 and $2 refer to the first and second method parameters
     * respectively in a non-static method (where $0 is 'this').
     */
    @Test
    void insertBefore_instanceMethod_twoParameterReferences() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        // For an instance method: $0 = this, $1 = a, $2 = b
        byte[] mutated = new JavassistMutator("multiply",
                "if ($1 == 0 || $2 == 0) return 0;").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Object instance = mutatedClass.getDeclaredConstructor().newInstance();
        Method m = mutatedClass.getMethod("multiply", int.class, int.class);

        // Guard fires when either argument is zero
        assertEquals(0, (int) m.invoke(instance, 5, 0),
                "Guard should short-circuit to 0 when second arg is 0");
        assertEquals(0, (int) m.invoke(instance, 0, 7),
                "Guard should short-circuit to 0 when first arg is 0");

        // Normal path is intact when neither argument is zero
        assertEquals(12, (int) m.invoke(instance, 3, 4),
                "Normal multiplication should be unaffected when both args are non-zero");
    }

    // -----------------------------------------------------------------------
    // Error-handling: original bytes returned on failure
    // -----------------------------------------------------------------------

    @Test
    void nonExistentMethod_returnsOriginalBytesUnchanged() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] result = new JavassistMutator("doesNotExist",
                "System.out.println(\"hi\");").mutate(original);

        assertArrayEquals(original, result,
                "A missing method name should leave the bytecode completely unchanged");
    }

    @Test
    void invalidSourceCode_returnsOriginalBytesUnchanged() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] result = new JavassistMutator("compute",
                "this is not valid Java !!!").mutate(original);

        assertArrayEquals(original, result,
                "A source compilation error should leave the bytecode completely unchanged");
    }

    // -----------------------------------------------------------------------
    // JavassistMutation interface
    // -----------------------------------------------------------------------

    /**
     * JavassistMutation.of(sourceCode) + seed produces the same bytecode as
     * the equivalent JavassistMutator(sourceCode, seed) call — the named
     * wrapper adds no observable difference.
     */
    @Test
    void mutationOf_producesIdenticalBytesToSeededStringConstructor() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);

        byte[] viaString   = new JavassistMutator("System.out.println(\"hi\");", 5L)
                .mutate(original);

        byte[] viaMutation = new JavassistMutator(
                JavassistMutation.of("System.out.println(\"hi\");"), 5L)
                .mutate(original);

        assertArrayEquals(viaString, viaMutation,
                "JavassistMutation.of() with a seed should be byte-for-byte identical "
                        + "to JavassistMutator(sourceCode, seed)");
    }

    /**
     * An enum constant implementing JavassistMutation can be used with a seed.
     * The seeded mutator picks a target from the class and injects the enum's
     * source code — bytecode should change and remain valid.
     */
    @Test
    void enumMutation_withSeed_changesBytecodeAndIsValid() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator(SampleMutations.LOG_ENTRY, 5L)
                .mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    /**
     * Using an enum mutation's source code with explicit method targeting still
     * produces the expected runtime behaviour — verifying the guard logic itself
     * independent of which target the seed would choose.
     */
    @Test
    void enumMutation_withExplicitTarget_changesRuntimeBehaviour() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator("compute",
                SampleMutations.GUARD_LARGE_INPUT.sourceCode()).mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        Method m = mutatedClass.getMethod("compute", int.class);

        assertEquals(-1, (int) m.invoke(null, 500), "Guard should fire for x > 100");
        assertEquals(15, (int) m.invoke(null,   5), "Normal path should be unaffected");
    }

    // -----------------------------------------------------------------------
    // Constructor injection tests
    // -----------------------------------------------------------------------

    /**
     * Targeting {@link JavassistMutator#CONSTRUCTOR} changes bytecode and the
     * result is valid JVM bytecode.
     */
    @Test
    void insertBefore_constructor_changesBytecodeAndIsValid() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator(JavassistMutator.CONSTRUCTOR,
                "System.out.println(\"constructing\");").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    /**
     * {@link JavassistMutator.InsertionPoint#AFTER} on a constructor runs the
     * injected code after the object is fully initialised (i.e. after the
     * original constructor body has executed).
     *
     * <p>The original constructor increments {@code constructorCallCount} by 1.
     * The injected code adds 100 to the same field.  After one instantiation
     * the field should therefore be 101.
     */
    @Test
    void insertAfter_constructor_runsAfterInitialisation() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator(JavassistMutator.CONSTRUCTOR,
                "constructorCallCount += 100;",
                JavassistMutator.InsertionPoint.AFTER).mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        mutatedClass.getDeclaredConstructor().newInstance();

        java.lang.reflect.Field f = mutatedClass.getField("constructorCallCount");
        assertEquals(101, f.getInt(null),
                "Original ++ gives 1, then injected += 100 gives 101");
    }

    /**
     * Injecting a {@code throw} via {@link JavassistMutator.InsertionPoint#BEFORE}
     * runs after {@code super()} but before the original constructor body, so
     * instantiation is effectively blocked.
     */
    @Test
    void insertBefore_constructor_throwBlocksInstantiation() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator(JavassistMutator.CONSTRUCTOR,
                "throw new RuntimeException(\"blocked\");").mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);

        Class<?> mutatedClass = MutatorTestUtil.loadFromBytes(SAMPLE, mutated);
        java.lang.reflect.InvocationTargetException ex = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> mutatedClass.getDeclaredConstructor().newInstance(),
                "Injected throw should propagate out of the constructor");

        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals("blocked", ex.getCause().getMessage());
    }

    // -----------------------------------------------------------------------
    // Target discovery
    // -----------------------------------------------------------------------

    /**
     * JavassistSample has 4 methods (compute, greet, getValue, multiply) plus
     * 1 constructor — 5 behaviors × 2 insertion points = 10 targets.
     */
    @Test
    void discoverTargets_returnsBeforeAndAfterForEveryBehavior() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);

        assertEquals(10, targets.size(),
                "5 behaviors × 2 insertion points should yield 10 targets");

        // Every behavior name appears exactly once in BEFORE and once in AFTER
        long beforeCount = targets.stream()
                .filter(t -> t.insertionPoint() == JavassistMutator.InsertionPoint.BEFORE)
                .count();
        long afterCount  = targets.stream()
                .filter(t -> t.insertionPoint() == JavassistMutator.InsertionPoint.AFTER)
                .count();
        assertEquals(5, beforeCount, "Should have one BEFORE entry per behavior");
        assertEquals(5, afterCount,  "Should have one AFTER entry per behavior");
    }

    @Test
    void discoverTargets_includesConstructorAndAllMethods() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);

        List<String> names = targets.stream()
                .map(JavassistMutator.InjectionTarget::behaviorName)
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of("<init>", "compute", "getValue", "greet", "multiply"), names);
    }

    // -----------------------------------------------------------------------
    // selectTarget — seed repeatability
    // -----------------------------------------------------------------------

    @Test
    void selectTarget_sameSeedAlwaysReturnsSameTarget() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);

        JavassistMutator.InjectionTarget first  = JavassistMutator.selectTarget(targets, 99L);
        JavassistMutator.InjectionTarget second = JavassistMutator.selectTarget(targets, 99L);

        assertEquals(first, second, "Same seed must always select the same target");
    }

    @Test
    void selectTarget_differentSeedsCanSelectDifferentTargets() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);

        // With 10 possible targets, two seeds are very unlikely to collide.
        // We pick seeds that are known to land on different targets.
        boolean anyDifference = false;
        JavassistMutator.InjectionTarget reference = JavassistMutator.selectTarget(targets, 0L);
        for (long s = 1; s <= 20; s++) {
            if (!JavassistMutator.selectTarget(targets, s).equals(reference)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "Different seeds should be able to select different targets");
    }

    @Test
    void selectTarget_emptyListThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JavassistMutator.selectTarget(List.of(), 0L),
                "selectTarget on an empty list should throw IllegalArgumentException");
    }

    // -----------------------------------------------------------------------
    // Seeded constructor — end-to-end
    // -----------------------------------------------------------------------

    @Test
    void seededMutator_changesBytecodeAndIsValid() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] mutated  = new JavassistMutator("System.out.println(\"injected\");", 7L)
                .mutate(original);

        MutatorTestUtil.assertBytecodeChanged(original, mutated);
        MutatorTestUtil.assertBytecodeValid(mutated);
    }

    @Test
    void seededMutator_sameSeedProducesIdenticalBytecode() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);
        byte[] first    = new JavassistMutator("System.out.println(\"x\");", 42L)
                .mutate(original);
        byte[] second   = new JavassistMutator("System.out.println(\"x\");", 42L)
                .mutate(original);

        assertArrayEquals(first, second,
                "The same seed must produce byte-for-byte identical output");
    }

    @Test
    void seededMutator_differentSeedsProduceDifferentBytecode() throws Exception {
        byte[] original = MutatorTestUtil.classBytes(JavassistSample.class);

        // Find two seeds that land on different targets (guaranteed within 20 tries
        // given 10 targets in JavassistSample).
        List<JavassistMutator.InjectionTarget> targets =
                JavassistMutator.discoverTargets(original);
        long seedA = 0L;
        long seedB = 1L;
        for (long s = 1; s <= 20; s++) {
            if (!JavassistMutator.selectTarget(targets, s)
                    .equals(JavassistMutator.selectTarget(targets, seedA))) {
                seedB = s;
                break;
            }
        }

        byte[] mutatedA = new JavassistMutator("System.out.println(\"y\");", seedA)
                .mutate(original);
        byte[] mutatedB = new JavassistMutator("System.out.println(\"y\");", seedB)
                .mutate(original);

        assertFalse(java.util.Arrays.equals(mutatedA, mutatedB),
                "Seeds that select different targets should produce different bytecode");
    }

    // -----------------------------------------------------------------------
    // Helper enum — defined here to keep the test self-contained
    // -----------------------------------------------------------------------

    enum SampleMutations implements JavassistMutation {
        /** Guard that short-circuits compute() for large inputs. Works only in int-returning
         *  methods with an int first parameter — use with explicit method targeting. */
        GUARD_LARGE_INPUT {
            @Override public String sourceCode() { return "if ($1 > 100) return -1;"; }
        },
        /** Simple log statement. Compiles in any non-native, non-abstract method or
         *  constructor — safe to use with seeded (random-target) mutation. */
        LOG_ENTRY {
            @Override public String sourceCode() { return "System.out.println(\"entry\");"; }
        }
    }
}
