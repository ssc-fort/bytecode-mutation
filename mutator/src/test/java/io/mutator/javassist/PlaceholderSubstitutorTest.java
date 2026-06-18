package io.mutator.javassist;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the typed {@code @in} placeholder engine and its generators. */
class PlaceholderSubstitutorTest {

    private static final Map<String, List<String>> NO_PARAMS = Map.of();

    private static String sub(String body, long seed) {
        return PlaceholderSubstitutor.substitute(body, seed, NO_PARAMS);
    }

    // -----------------------------------------------------------------------
    // Format detection + comment stripping
    // -----------------------------------------------------------------------

    @Test
    void detectsAtInFormatOnly() {
        assertTrue(PlaceholderSubstitutor.hasDeclarations("// @in <x> java.lang.String payload\nfoo(<x>);"));
        assertFalse(PlaceholderSubstitutor.hasDeclarations("foo(\"#STRING#\");"));
        assertFalse(PlaceholderSubstitutor.hasDeclarations("int y = 1;"));
    }

    @Test
    void stripsAtInCommentLines() {
        String out = sub("// @in <x> java.lang.String payload\nString s = <x>;", 1L);
        assertFalse(out.contains("@in"), "Declaration comments must be removed: " + out);
        assertFalse(out.contains("<x>"), "Placeholder must be replaced: " + out);
        assertTrue(out.startsWith("String s = "), out);
    }

    // -----------------------------------------------------------------------
    // Generation by Java type
    // -----------------------------------------------------------------------

    @Test
    void generatesByJavaType() {
        assertTrue(sub("// @in <v> java.lang.String payload\n<v>", 3L).matches("\"[^\"]*\""));
        assertTrue(sub("// @in <v> byte[] xor-key\n<v>", 3L).endsWith(".getBytes()"));
        assertDoesNotThrow(() -> Integer.parseInt(sub("// @in <v> int tcp-port\n<v>", 3L)));
        assertTrue(sub("// @in <v> long hash-value\n<v>", 3L).endsWith("L"));
        assertTrue(sub("// @in <v> java.lang.Class java-class\n<v>", 3L).endsWith(".class"));
        assertEquals("new java.lang.Object()", sub("// @in <v> java.lang.Object java-object\n<v>", 3L));
        assertTrue(sub("// @in <v> void java-statement\n<v>", 3L).endsWith(";"));
    }

    @Test
    void unknownReferenceTypeFallsBackToNullCast() {
        assertEquals("(java.security.PublicKey) null",
                sub("// @in <v> java.security.PublicKey public-key\n<v>", 3L));
    }

    // -----------------------------------------------------------------------
    // Generation by semantic tag
    // -----------------------------------------------------------------------

    @Test
    void generatesBySemanticTag() {
        assertTrue(sub("// @in <v> java.lang.String url\n<v>", 5L).matches("\"https?://[^\"]+\""));
        assertTrue(sub("// @in <v> java.lang.String file-path\n<v>", 5L).matches("\"/[^\"]+\""));
        assertTrue(sub("// @in <v> java.lang.String java-class-name\n<v>", 5L).startsWith("\"com."));
        // base64-string must be decodable Base64.
        String b64 = sub("// @in <v> java.lang.String base64-string\n<v>", 5L);
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(b64.replace("\"", "")));
    }

    // -----------------------------------------------------------------------
    // Repeated placeholders + reproducibility
    // -----------------------------------------------------------------------

    @Test
    void repeatedPlaceholderGetsTheSameValueEverywhere() {
        // <key> appears twice and must be identical at both sites.
        String out = sub("// @in <key> byte[] xor-key\n<key> | <key>", 9L);
        String[] sides = out.split(" \\| ");
        assertEquals(2, sides.length, out);
        assertEquals(sides[0], sides[1], "Repeated placeholder must resolve to one value: " + out);
    }

    @Test
    void sameSeedIsReproducible() {
        String body = "// @in <a> java.lang.String url\n// @in <b> int tcp-port\n<a>:<b>";
        assertEquals(sub(body, 42L), sub(body, 42L));
    }

    // -----------------------------------------------------------------------
    // Type-aware parameter injection
    // -----------------------------------------------------------------------

    @Test
    void usesMatchingTypedParameterSometimesAndLiteralSometimes() {
        String body = "// @in <v> java.lang.String payload\n<v>";
        Map<String, List<String>> params = Map.of("java.lang.String", List.of("$1", "$2"));
        boolean sawParam = false, sawLiteral = false;
        for (long s = 0; s < 60 && !(sawParam && sawLiteral); s++) {
            String out = PlaceholderSubstitutor.substitute(body, s, params);
            if (out.equals("$1") || out.equals("$2")) sawParam = true;
            else if (out.startsWith("\"")) sawLiteral = true;
        }
        assertTrue(sawParam,   "a same-typed parameter should sometimes be used");
        assertTrue(sawLiteral, "a generated literal should sometimes be used");
    }

    @Test
    void neverUsesParameterOfADifferentType() {
        String body = "// @in <v> java.lang.String payload\n<v>";
        Map<String, List<String>> intParam = Map.of("int", List.of("$1"));
        for (long s = 0; s < 40; s++) {
            assertFalse(PlaceholderSubstitutor.substitute(body, s, intParam).contains("$"),
                    "an int parameter must not satisfy a String placeholder");
        }
    }

    @Test
    void byteArrayPlaceholderCanUseAByteArrayParameter() {
        String body = "// @in <k> byte[] xor-key\n<k>";
        Map<String, List<String>> params = Map.of("byte[]", List.of("$1"));
        boolean sawParam = false;
        for (long s = 0; s < 60 && !sawParam; s++) {
            if (PlaceholderSubstitutor.substitute(body, s, params).equals("$1")) sawParam = true;
        }
        assertTrue(sawParam, "a byte[] parameter should be a candidate for a byte[] placeholder");
    }

    @Test
    void statementPlaceholderNeverUsesAParameter() {
        String body = "// @in <s> void java-statement\n<s>";
        Map<String, List<String>> params = Map.of("void", List.of("$1"));
        for (long s = 0; s < 40; s++) {
            assertFalse(PlaceholderSubstitutor.substitute(body, s, params).contains("$1"),
                    "a void statement placeholder must never resolve to a parameter");
        }
    }
}
