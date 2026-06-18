package io.mutator.javassist;

import net.java.quickcheck.Generator;
import net.java.quickcheck.generator.PrimitiveGenerators;
import net.java.quickcheck.generator.distribution.RandomConfiguration;
import net.java.quickcheck.generator.support.FixedValuesGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared QuickCheck-backed random primitives used by the snippet placeholder
 * generators ({@link PlaceholderSubstitutor}) and the legacy {@link SnippetToken}
 * substitution.
 *
 * <p>All values are drawn from QuickCheck's single process-global RNG.  Callers
 * must hold {@link #RNG_LOCK} for the whole {@code seed(...)}-then-generate
 * sequence so that a substitution is both reproducible (the seed fully
 * determines the draws) and safe against concurrent callers interleaving on the
 * shared RNG.
 */
final class RandomValues {

    private RandomValues() {}

    /** Monitor guarding the QuickCheck global RNG; shared by all substitution entry points. */
    static final Object RNG_LOCK = new Object();

    /** Classpath location of the dictionary used for string fragments. */
    private static final String WORDS_RESOURCE = "/words-10k.txt";

    private static final List<String>      WORDS    = loadWords();
    private static final Generator<String> WORD_GEN = new FixedValuesGenerator<>(WORDS);

    /** Re-seeds the global RNG; call once at the start of a locked substitution. */
    static void seed(long seed) {
        RandomConfiguration.setSeed(seed);
    }

    /** A random dictionary word. */
    static String word() {
        return WORD_GEN.next();
    }

    /** A uniform integer in {@code [lo, hi]} (inclusive). */
    static int intInRange(int lo, int hi) {
        return PrimitiveGenerators.integers(lo, hi).next();
    }

    /** A random {@code long}. */
    static long longValue() {
        return PrimitiveGenerators.longs().next();
    }

    /** A uniform index in {@code [0, n)}. */
    static int bounded(int n) {
        return PrimitiveGenerators.integers(0, n - 1).next();
    }

    /** A 50/50 coin flip. */
    static boolean coin() {
        return PrimitiveGenerators.integers(0, 1).next() == 0;
    }

    /** A uniform pick from the given values. */
    static String oneOf(String... values) {
        return values[bounded(values.length)];
    }

    /** A uniform pick from the given list. */
    static <T> T oneOf(List<T> values) {
        return new FixedValuesGenerator<>(values).next();
    }

    /** Wraps {@code s} in double quotes to form a Java string literal. */
    static String quote(String s) {
        return "\"" + s + "\"";
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<String> loadWords() {
        try (InputStream in = RandomValues.class.getResourceAsStream(WORDS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Dictionary resource not found on classpath: " + WORDS_RESOURCE);
            }
            String       raw   = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String token : raw.split(",")) {
                String w = token.strip();
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            if (words.isEmpty()) {
                throw new IllegalStateException("Dictionary resource is empty: " + WORDS_RESOURCE);
            }
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read dictionary " + WORDS_RESOURCE, e);
        }
    }
}
