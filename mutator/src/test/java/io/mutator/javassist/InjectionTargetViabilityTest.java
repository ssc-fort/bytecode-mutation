package io.mutator.javassist;

import io.mutator.MutationResult;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link JavassistMutator#discoverTargets} viability
 * filtering: non-injectable behaviours (static initializers, bodyless methods)
 * must never be offered as targets, so seeded mutation cannot fail with
 * "no constructors found" or "no method body".
 */
class InjectionTargetViabilityTest {

    /**
     * A type whose only constructor-like behaviour is the static initializer
     * {@code <clinit>}. Pre-fix, discovery mislabelled the {@code <clinit>} as a
     * {@code CONSTRUCTOR} target, which then failed to resolve.
     */
    private static byte[] interfaceWithClinitAndStaticMethod() throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass cc = pool.makeInterface("io.mutator.samples.ClinitOnlyIface");
        cc.addMethod(CtMethod.make("public static int answer() { return 42; }", cc));
        CtConstructor clinit = cc.makeClassInitializer();   // forces a <clinit>
        clinit.setBody("{ System.nanoTime(); }");
        byte[] bytes = cc.toBytecode();
        cc.detach();
        return bytes;
    }

    @Test
    void staticInitializerIsNotOfferedAsAConstructorTarget() throws Exception {
        byte[] bytes = interfaceWithClinitAndStaticMethod();

        List<JavassistMutator.InjectionTarget> targets = JavassistMutator.discoverTargets(bytes);

        assertTrue(targets.stream().noneMatch(t -> JavassistMutator.CONSTRUCTOR.equals(t.behaviorName())),
                "the static initializer must not appear as a constructor target: " + targets);
        assertTrue(targets.stream().anyMatch(t -> "answer".equals(t.behaviorName())),
                "the concrete static method should be a viable target: " + targets);
    }

    @Test
    void seededMutationNeverFailsWithNoConstructorsFound() throws Exception {
        byte[] bytes = interfaceWithClinitAndStaticMethod();
        int targetCount = JavassistMutator.discoverTargets(bytes).size();

        // Every seed must resolve to a viable target (the static method), never
        // the phantom constructor that produced inject_failed:other before.
        boolean anySuccess = false;
        for (int seed = 0; seed < Math.max(targetCount, 1); seed++) {
            MutationResult r = new JavassistMutator("System.out.println(\"x\");", (long) seed)
                    .mutateWithResult(bytes);
            assertNotEquals("inject_failed:other", r.failureDetail(),
                    "should never fail with 'no constructors found'");
            anySuccess |= r.succeeded();
        }
        assertTrue(anySuccess, "the static method should be mutable");
    }

    /**
     * An abstract class with an overloaded method name where one overload is
     * abstract (no body) and another is concrete. Discovery validates the
     * concrete overload and adds the name; resolution must then pick the
     * concrete overload too, not the abstract one — otherwise injection fails
     * with "no method body" (the real-corpus bug, e.g. {@code addEntry} on
     * commons-compress {@code LZWInputStream}).
     */
    @Test
    void overloadedNameResolvesToTheInjectableOverload() throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass cc = pool.makeClass("io.mutator.samples.OverloadedAbstract");
        cc.setModifiers(javassist.Modifier.ABSTRACT | cc.getModifiers());
        // Abstract overload declared FIRST (so getDeclaredMethod(name) would
        // return this bodyless one), concrete overload declared second.
        cc.addMethod(CtMethod.make("public abstract int addEntry(int code);", cc));
        cc.addMethod(CtMethod.make("public int addEntry(int code, int max) { return code + max; }", cc));
        byte[] bytes = cc.toBytecode();
        cc.detach();

        int targetCount = JavassistMutator.discoverTargets(bytes).size();
        boolean injectedAddEntry = false;
        for (int seed = 0; seed < targetCount; seed++) {
            MutationResult r = new JavassistMutator("System.out.println(\"x\");", (long) seed)
                    .mutateWithResult(bytes);
            assertNotEquals("inject_failed:compile", r.failureDetail(),
                    "must not fail with 'no method body' on the abstract overload");
            if ("addEntry".equals(r.targetName()) && r.succeeded()) {
                injectedAddEntry = true;
            }
        }
        assertTrue(injectedAddEntry, "the concrete addEntry overload should be injectable");
    }

    /**
     * A class whose only declared method is abstract still yields no injectable
     * targets, but discovery must report it cleanly (empty list) rather than
     * offering a bodyless target.
     */
    @Test
    void abstractMethodIsNotOfferedAsATarget() throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass cc = pool.makeClass("io.mutator.samples.AbstractOnly");
        cc.setModifiers(javassist.Modifier.ABSTRACT | cc.getModifiers());
        cc.addMethod(CtMethod.make("public abstract int todo();", cc));
        byte[] bytes = cc.toBytecode();
        cc.detach();

        List<JavassistMutator.InjectionTarget> targets = JavassistMutator.discoverTargets(bytes);

        assertTrue(targets.stream().noneMatch(t -> "todo".equals(t.behaviorName())),
                "an abstract method must not be a target: " + targets);
        // The implicit default constructor is still a valid target.
        assertTrue(targets.stream().anyMatch(t -> JavassistMutator.CONSTRUCTOR.equals(t.behaviorName())),
                "the default constructor should remain injectable: " + targets);
    }
}
