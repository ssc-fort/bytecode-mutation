package io.mutator.javassist;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SnippetToken#substituteAll(String, long, java.util.List)} —
 * covering the QuickCheck-backed, word-list-sourced value generation, the
 * three token positions (standalone / embedded / expression), reproducibility,
 * and method-argument candidacy.
 */
class SnippetTokenTest {

    private static final List<String> NO_PARAMS = List.of();

    // -----------------------------------------------------------------------
    // Reproducibility
    // -----------------------------------------------------------------------

    @Test
    void sameSeedProducesIdenticalSubstitution() {
        String body = "x = \"#STRING#\"; p = #INT#; b = #BYTES#;";
        String a = SnippetToken.substituteAll(body, 123L, NO_PARAMS);
        String b = SnippetToken.substituteAll(body, 123L, NO_PARAMS);
        assertEquals(a, b, "Same seed must yield identical substitution");
    }

    @Test
    void differentSeedsCanProduceDifferentValues() {
        String body = "\"#STRING#\"";
        Set<String> seen = new HashSet<>();
        for (long s = 0; s < 20; s++) {
            seen.add(SnippetToken.substituteAll(body, s, NO_PARAMS));
        }
        assertTrue(seen.size() > 1, "Different seeds should produce varied strings");
    }

    // -----------------------------------------------------------------------
    // Word-list sourcing
    // -----------------------------------------------------------------------

    @Test
    void stringValuesAreDrawnFromTheDictionary() throws Exception {
        Set<String> dictionary = loadDictionary();

        for (long s = 0; s < 50; s++) {
            String out = SnippetToken.substituteAll("\"#STRING#\"", s, NO_PARAMS);
            // Strip the surrounding quotes, then split the "word_word" form.
            String inner = out.substring(1, out.length() - 1);
            for (String part : inner.split("_")) {
                assertTrue(dictionary.contains(part),
                        "Generated fragment '" + part + "' should come from words-10k.txt");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Token positions
    // -----------------------------------------------------------------------

    @Test
    void standaloneStringTokenBecomesQuotedLiteral() {
        String out = SnippetToken.substituteAll("\"#FILEPATH#\"", 7L, NO_PARAMS);
        assertTrue(out.startsWith("\"") && out.endsWith("\""),
                "Standalone string token should be a quoted literal: " + out);
        assertEquals(2, countQuotes(out), "Should contain exactly one balanced pair of quotes");
    }

    @Test
    void embeddedTokenKeepsSurroundingLiteralBalanced() {
        // Token sits at the end of a larger string literal.
        String out = SnippetToken.substituteAll("\"@myorg:registry=#HTTP_URL#\"", 7L, NO_PARAMS);
        assertTrue(out.startsWith("\"@myorg:registry="),
                "Embedded prefix should be preserved: " + out);
        assertEquals(2, countQuotes(out),
                "Embedded substitution must not unbalance the literal's quotes: " + out);
    }

    @Test
    void intTokenIsBareNumber() {
        String out = SnippetToken.substituteAll("#INT#", 7L, NO_PARAMS);
        assertDoesNotThrow(() -> Integer.parseInt(out), "INT should be a bare integer: " + out);
    }

    @Test
    void bytesTokenIsGetBytesExpression() {
        String out = SnippetToken.substituteAll("#BYTES#", 7L, NO_PARAMS);
        assertTrue(out.endsWith(".getBytes()"), "BYTES should be a getBytes() expression: " + out);
    }

    // -----------------------------------------------------------------------
    // Method-argument candidacy
    // -----------------------------------------------------------------------

    @Test
    void stringTokenCanResolveToAMethodParameter() {
        List<String> params = List.of("$1", "$2");
        boolean sawParam   = false;
        boolean sawLiteral = false;
        for (long s = 0; s < 60 && !(sawParam && sawLiteral); s++) {
            String out = SnippetToken.substituteAll("\"#STRING#\"", s, params);
            if (out.equals("$1") || out.equals("$2")) {
                sawParam = true;
            } else if (out.startsWith("\"") && out.endsWith("\"")) {
                sawLiteral = true;
            }
        }
        assertTrue(sawParam,   "A String parameter ($1/$2) should sometimes be chosen");
        assertTrue(sawLiteral, "A generated literal should sometimes be chosen");
    }

    /**
     * The chance of using a parameter must be ~50% and, crucially, independent of
     * how many String parameters the method declares — otherwise methods with
     * more string args would pull in parameters more often, biasing the data.
     */
    @Test
    void parameterIsChosenAboutHalfTheTime_regardlessOfParameterCount() {
        double oneParam   = parameterRate(List.of("$1"));
        double threeParam = parameterRate(List.of("$1", "$2", "$3"));

        assertTrue(0.44 <= oneParam && oneParam <= 0.56,
                "With 1 parameter, parameter-use rate should be ~50%, was " + oneParam);
        assertTrue(0.44 <= threeParam && threeParam <= 0.56,
                "With 3 parameters, parameter-use rate should be ~50%, was " + threeParam);
    }

    /** Fraction of seeds (out of 4000) where "#STRING#" resolves to a parameter. */
    private static double parameterRate(List<String> params) {
        int trials = 4000, paramHits = 0;
        for (long s = 0; s < trials; s++) {
            if (SnippetToken.substituteAll("\"#STRING#\"", s, params).startsWith("$")) {
                paramHits++;
            }
        }
        return paramHits / (double) trials;
    }

    @Test
    void stringTokenNeverUsesParameterWhenNoneAvailable() {
        for (long s = 0; s < 30; s++) {
            String out = SnippetToken.substituteAll("\"#STRING#\"", s, NO_PARAMS);
            assertFalse(out.contains("$"),
                    "With no parameters, only literals may be produced: " + out);
        }
    }

    @Test
    void expressionTokensIgnoreParameters() {
        List<String> params = List.of("$1");
        for (long s = 0; s < 30; s++) {
            assertFalse(SnippetToken.substituteAll("#INT#", s, params).contains("$"),
                    "INT must never resolve to a parameter");
            assertFalse(SnippetToken.substituteAll("#BYTES#", s, params).equals("$1"),
                    "BYTES must never resolve to a parameter");
        }
    }

    @Test
    void unknownTokenIsLeftIntact() {
        assertEquals("\"#NOPE#\"", SnippetToken.substituteAll("\"#NOPE#\"", 1L, NO_PARAMS));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int countQuotes(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') n++;
        }
        return n;
    }

    private static Set<String> loadDictionary() throws Exception {
        try (InputStream in = SnippetToken.class.getResourceAsStream("/words-10k.txt")) {
            assertNotNull(in, "words-10k.txt must be on the test classpath");
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> words = new HashSet<>();
            for (String w : raw.split(",")) {
                String t = w.strip();
                if (!t.isEmpty()) words.add(t);
            }
            return words;
        }
    }
}
