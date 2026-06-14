package io.mutator.javassist;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import io.mutator.MutationResult;
import io.mutator.Mutator;
import org.pitest.reloc.asm.ClassReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A {@link Mutator} that injects arbitrary Java source code into a method or
 * constructor using <a href="https://www.javassist.org/">Javassist</a>.
 *
 * <p>Javassist compiles the supplied source fragment against the class's existing
 * bytecode, so the injected code can refer to the target's parameters via
 * the Javassist special variables:
 * <ul>
 *   <li>{@code $0}          — {@code this} (undefined in static methods)</li>
 *   <li>{@code $1, $2, ...} — parameters in declaration order</li>
 *   <li>{@code $args}       — all parameters as {@code Object[]}</li>
 *   <li>{@code $_}          — the return value ({@link InsertionPoint#AFTER} in
 *                             non-void methods only)</li>
 * </ul>
 *
 * <h3>Explicit targeting</h3>
 * <p>Supply the method name (or {@link #CONSTRUCTOR}) and source code directly.
 * {@link InsertionPoint#BEFORE} inserts before the body;
 * {@link InsertionPoint#AFTER} inserts just before every return point.
 *
 * <h3>Random target selection</h3>
 * <p>Use {@link #discoverTargets(byte[])} to enumerate every injectable point in
 * a class — one {@code BEFORE} and one {@code AFTER} entry per non-abstract,
 * non-native method and constructor.  Call {@link #selectTarget(List, long)} with
 * a seed to pick one deterministically, or use the
 * {@link #JavassistMutator(String, long)} constructor to do both steps
 * automatically inside {@link #mutate(byte[])}.  The same seed on the same class
 * always resolves to the same injection point, making the process fully repeatable.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // Explicit method target
 * new JavassistMutator("processOrder", "System.out.println($1);")
 *
 * // Constructor
 * new JavassistMutator("<init>", "validate();", InsertionPoint.AFTER)
 *
 * // Seeded random target — anonymous source string
 * new JavassistMutator("System.out.println(\"hit\");", 42L)
 *
 * // Seeded random target — named mutation object
 * new JavassistMutator(myMutation, 42L)
 *
 * // Manual discovery + selection
 * List<InjectionTarget> targets = JavassistMutator.discoverTargets(classBytes);
 * InjectionTarget t = JavassistMutator.selectTarget(targets, seed);
 * }</pre>
 *
 * <p>If the injection fails (method/constructor not found, source does not
 * compile, etc.) the original bytes are returned unchanged and a message is
 * printed to {@code System.out}.
 */
public class JavassistMutator implements Mutator {

    // -----------------------------------------------------------------------
    // Nested types
    // -----------------------------------------------------------------------

    /** Where in the target the source fragment is inserted. */
    public enum InsertionPoint {
        /** Inserted at the very start of the body, before any existing code. */
        BEFORE,
        /** Inserted just before every return point of the method/constructor. */
        AFTER
    }

    /**
     * A discovered injection point: a behavior name paired with an insertion
     * point.  Obtain instances via {@link #discoverTargets(byte[])}.
     *
     * @param behaviorName   method name, or {@link #CONSTRUCTOR} ({@code "<init>"})
     * @param insertionPoint where code would be inserted
     */
    public record InjectionTarget(String behaviorName, InsertionPoint insertionPoint) {
        @Override
        public String toString() {
            return behaviorName + ":" + insertionPoint;
        }
    }

    // -----------------------------------------------------------------------
    // Constants / fields
    // -----------------------------------------------------------------------

    /**
     * Sentinel method name that selects the constructor instead of a named method.
     * Matches the JVM internal name for constructors.
     */
    public static final String CONSTRUCTOR = "<init>";

    private final String         methodName;      // null in seeded mode
    private final String         sourceCode;
    private final InsertionPoint insertionPoint;  // null in seeded mode
    private final Long           seed;            // null in explicit mode

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Constructs a seeded random-target mutator from a {@link JavassistMutation}.
     *
     * <p>Equivalent to {@link #JavassistMutator(String, long)
     * JavassistMutator(mutation.sourceCode(), seed)}: the injection target is
     * discovered from the class bytes at {@link #mutate} time and selected
     * deterministically using {@code seed}.
     *
     * @param mutation the source-code fragment to inject
     * @param seed     seed for the target-selection RNG
     */
    public JavassistMutator(JavassistMutation mutation, long seed) {
        this(mutation.sourceCode(), seed);
    }

    /**
     * Convenience constructor that inserts code {@link InsertionPoint#BEFORE}.
     *
     * @param methodName name of the method, or {@link #CONSTRUCTOR} ({@code "<init>"})
     * @param sourceCode Javassist-compatible Java source fragment
     */
    public JavassistMutator(String methodName, String sourceCode) {
        this(methodName, sourceCode, InsertionPoint.BEFORE);
    }

    /**
     * Explicit-target constructor.
     *
     * @param methodName     name of the method, or {@link #CONSTRUCTOR} ({@code "<init>"})
     * @param sourceCode     Javassist-compatible Java source fragment
     * @param insertionPoint where to insert the fragment
     */
    public JavassistMutator(String methodName, String sourceCode,
            InsertionPoint insertionPoint) {
        this.methodName     = methodName;
        this.sourceCode     = sourceCode;
        this.insertionPoint = insertionPoint;
        this.seed           = null;
    }

    /**
     * Seeded random-target constructor.
     *
     * <p>When {@link #mutate(byte[])} is called the mutator discovers every
     * injectable point in the supplied class and picks one deterministically
     * using {@code seed}.  The same seed on the same class always selects the
     * same injection point, so results are fully repeatable.
     *
     * @param sourceCode Javassist-compatible Java source fragment
     * @param seed       seed for the target-selection RNG
     */
    public JavassistMutator(String sourceCode, long seed) {
        this.sourceCode     = sourceCode;
        this.seed           = seed;
        this.methodName     = null;
        this.insertionPoint = null;
    }

    // -----------------------------------------------------------------------
    // Target discovery
    // -----------------------------------------------------------------------

    /**
     * Returns every valid injection target in the given class — one
     * {@link InsertionPoint#BEFORE} and one {@link InsertionPoint#AFTER} entry
     * for each non-abstract, non-native method and constructor.
     *
     * <p>Abstract and native behaviors have no body and cannot be injected into,
     * so they are excluded.  When a class has overloaded methods sharing the same
     * name, only one entry pair is emitted for that name (Javassist resolves to
     * the first declared overload).  Similarly, when a class has multiple
     * constructors only one {@link #CONSTRUCTOR} pair is emitted.
     *
     * @param classBytes raw {@code .class} file content
     * @return ordered list of injection targets; empty if none found or on error
     */
    public static List<InjectionTarget> discoverTargets(byte[] classBytes) {
        ClassReader cr         = new ClassReader(classBytes);
        String      binaryName = cr.getClassName().replace('/', '.');

        try {
            ClassPool pool = new ClassPool(true);
            pool.insertClassPath(new ByteArrayClassPath(binaryName, classBytes));
            CtClass cc = pool.get(binaryName);

            List<InjectionTarget> targets = buildTargetList(cc);
            cc.detach();
            return targets;
        } catch (Exception e) {
            System.out.println("JavassistMutator: failed to discover targets in "
                    + binaryName + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Deterministically selects one target from {@code targets} using {@code seed}
     * as the RNG seed.  The same {@code (targets, seed)} pair always returns the
     * same element, making mutation runs repeatable.
     *
     * @param targets non-empty list returned by {@link #discoverTargets(byte[])}
     * @param seed    RNG seed
     * @return the selected target
     * @throws IllegalArgumentException if {@code targets} is empty
     */
    public static InjectionTarget selectTarget(List<InjectionTarget> targets, long seed) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Target list is empty — no injectable behaviors found in this class");
        }
        return targets.get(new Random(seed).nextInt(targets.size()));
    }

    // -----------------------------------------------------------------------
    // Mutator
    // -----------------------------------------------------------------------

    @Override
    public byte[] mutate(byte[] classBytes) {
        return mutateWithResult(classBytes).mutatedBytes();
    }

    /**
     * Like {@link #mutate(byte[])}, but also returns metadata about the selected
     * injection site regardless of whether injection succeeded.
     *
     * <p>{@link MutationResult#succeeded()} distinguishes three outcomes:
     * <ul>
     *   <li><b>No injectable targets</b> — {@code succeeded=false},
     *       {@code targetName=null}, {@code lineNumber=-1}</li>
     *   <li><b>Injection failed</b> (compile error, type not found, etc.) —
     *       {@code succeeded=false}; {@code targetName} and {@code lineNumber}
     *       reflect the target that was selected before the failure</li>
     *   <li><b>Success</b> — {@code succeeded=true}, all fields populated</li>
     * </ul>
     *
     * @param classBytes raw {@code .class} file content to mutate
     * @return a {@link MutationResult} describing the outcome
     */
    public MutationResult mutateWithResult(byte[] classBytes) {
        ClassReader cr         = new ClassReader(classBytes);
        String      binaryName = cr.getClassName().replace('/', '.');

        // Declared outside the try block so the catch can return whatever was
        // captured before the failure point.
        String         targetName = null;
        InsertionPoint point      = null;
        int            lineNumber = -1;

        try {
            ClassPool pool = new ClassPool(true);
            pool.insertClassPath(new ByteArrayClassPath(binaryName, classBytes));

            CtClass cc = pool.get(binaryName);

            if (seed != null) {
                List<InjectionTarget> targets = buildTargetList(cc);
                if (targets.isEmpty()) {
                    String detail = classifyEmptyTargets(cc);
                    System.out.println("JavassistMutator: no injectable behaviors in "
                            + binaryName + " (" + detail + ")");
                    cc.detach();
                    return new MutationResult(classBytes, false, null, -1, detail);
                }
                InjectionTarget chosen = selectTarget(targets, seed);
                System.out.printf("JavassistMutator: seed %d selected %s in %s%n",
                        seed, chosen, binaryName);
                targetName = chosen.behaviorName();
                point      = chosen.insertionPoint();
            } else {
                targetName = methodName;
                point      = insertionPoint;
            }

            CtBehavior behavior = resolveBehavior(cc, binaryName, targetName);
            lineNumber = lineNumberOf(behavior);

            switch (point) {
                case BEFORE -> behavior.insertBefore(sourceCode);
                case AFTER  -> behavior.insertAfter(sourceCode);
            }

            byte[] result = cc.toBytecode();
            cc.detach();
            return new MutationResult(result, true, targetName, lineNumber, null);

        } catch (Exception e) {
            // targetName is null  → exception fired before a target was selected
            //                       (class loading or target-discovery phase)
            // targetName non-null → a target was selected but injection failed
            String detail;
            if (targetName == null) {
                detail = "no_site:discover_failed";
            } else {
                String exType = e.getClass().getSimpleName();
                if (exType.equals("CannotCompileException")) {
                    detail = "inject_failed:compile";
                } else if (exType.equals("NotFoundException")) {
                    detail = "inject_failed:not_found";
                } else {
                    detail = "inject_failed:other";
                }
            }
            System.out.println("JavassistMutator: injection into "
                    + binaryName + "#"
                    + (seed != null ? "<seed=" + seed + ">" : methodName)
                    + " failed (" + detail + "): " + e.getMessage());
            return new MutationResult(classBytes, false, targetName, lineNumber, detail);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the source line number of the first bytecode in {@code behavior},
     * or {@code -1} if the class was compiled without line-number debug info.
     */
    private static int lineNumberOf(CtBehavior behavior) {
        try {
            return behavior.getMethodInfo().getLineNumber(0);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns a {@code no_site:*} failure-detail string that describes why
     * {@code cc} has no injectable targets.
     *
     * <ul>
     *   <li>{@code no_site:annotation}           — annotation type</li>
     *   <li>{@code no_site:interface}             — interface</li>
     *   <li>{@code no_site:no_behaviors}          — no declared methods or constructors</li>
     *   <li>{@code no_site:all_abstract_or_native}— has behaviors but all are abstract/native</li>
     * </ul>
     */
    private static String classifyEmptyTargets(CtClass cc) {
        if (cc.isAnnotation()) return "no_site:annotation";
        if (cc.isInterface())  return "no_site:interface";
        try {
            if (cc.getDeclaredBehaviors().length == 0) return "no_site:no_behaviors";
        } catch (Exception ignored) {}
        return "no_site:all_abstract_or_native";
    }

    /**
     * Builds the deduplicated target list from an already-loaded {@link CtClass}.
     * Shared by {@link #discoverTargets(byte[])} and {@link #mutateWithResult(byte[])}.
     */
    private static List<InjectionTarget> buildTargetList(CtClass cc) throws Exception {
        List<InjectionTarget> targets = new ArrayList<>();
        Set<String>           seen    = new HashSet<>();

        for (CtBehavior behavior : cc.getDeclaredBehaviors()) {
            int mod = behavior.getModifiers();
            if ((mod & (Modifier.ABSTRACT | Modifier.NATIVE)) != 0) {
                continue;
            }
            String name = (behavior instanceof CtConstructor) ? CONSTRUCTOR : behavior.getName();
            if (seen.add(name)) {   // skip duplicate names (overloads, multiple ctors)
                targets.add(new InjectionTarget(name, InsertionPoint.BEFORE));
                targets.add(new InjectionTarget(name, InsertionPoint.AFTER));
            }
        }
        return targets;
    }

    /**
     * Resolves the target {@link CtBehavior} — either a {@link CtMethod} or a
     * {@link CtConstructor} — from the given name.
     */
    private static CtBehavior resolveBehavior(CtClass cc, String binaryName,
            String targetName) throws Exception {
        if (CONSTRUCTOR.equals(targetName)) {
            CtConstructor[] ctors = cc.getDeclaredConstructors();
            if (ctors.length == 0) {
                throw new Exception("no constructors found in " + binaryName);
            }
            if (ctors.length > 1) {
                System.out.printf("JavassistMutator: %s has %d constructors; "
                        + "targeting the first declared one%n", binaryName, ctors.length);
            }
            return ctors[0];
        }
        return cc.getDeclaredMethod(targetName);
    }
}
