package io.mutator.samples;

/**
 * Target class for JavassistMutatorTest.
 * Each method is designed to make a specific Javassist injection clearly observable.
 */
public class JavassistSample {

    /** Tracks how many times the constructor ran — observable without a database/log. */
    public static int constructorCallCount = 0;

    /** Default constructor. Useful for testing constructor injection via "<init>". */
    public JavassistSample() {
        constructorCallCount++;
    }

    /** Returns x + 10. Useful for testing guard-clause injection via $1. */
    public static int compute(int x) {
        return x + 10;
    }

    /** Returns a greeting. Useful for testing null-guard injection via $1 (reference type). */
    public static String greet(String name) {
        return "Hello, " + name;
    }

    /** Always returns 42. Useful for testing return-value modification via $_. */
    public static int getValue() {
        return 42;
    }

    /** Instance method. Useful for verifying $1/$2 work for non-static methods. */
    public int multiply(int a, int b) {
        return a * b;
    }
}
