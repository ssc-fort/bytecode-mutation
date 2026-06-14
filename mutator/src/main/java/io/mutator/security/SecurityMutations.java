package io.mutator.security;

import io.mutator.Mutator;
import io.mutator.pitest.PitestMutator;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;

import java.nio.file.Path;

/**
 * Catalogue of PIT-based security mutation operators.
 *
 * <p>Each constant wraps one of the {@link MethodMutatorFactory} implementations
 * in this package and delegates to {@link PitestMutator} to apply the first
 * matching mutation site found in the target class.
 *
 * <h3>Operators</h3>
 * <dl>
 *   <dt>Cookie flags</dt>
 *   <dd>{@link #COOKIE_HTTPONLY_DISABLE}, {@link #COOKIE_SECURE_DISABLE} —
 *       remove security flags from cookie configuration.</dd>
 *
 *   <dt>XML parser hardening</dt>
 *   <dd>{@link #XML_DISABLE_DOCTYPE_SAX}, {@link #XML_DISABLE_DOCTYPE_XMLREADER},
 *       {@link #XML_DISABLE_DOS_SAX}, {@link #XML_DISABLE_DOS_XMLREADER} —
 *       remove DOCTYPE/external-entity restrictions, enabling XXE and DoS attacks.</dd>
 *
 *   <dt>TLS / network</dt>
 *   <dd>{@link #HOSTNAME_VERIFY_TRUE} — replace hostname verification with
 *       unconditional {@code true}.</dd>
 *   <dd>{@link #REMOVE_SSL_SOCKET} — strip SSL wrapping from a socket.</dd>
 *
 *   <dt>Cryptography weakening</dt>
 *   <dd>{@link #RSA_SHORT_KEY} — reduce RSA key size to 512 bits.</dd>
 *   <dd>{@link #BLOWFISH_SHORT_KEY} — reduce Blowfish key size to 64 bits.</dd>
 *   <dd>{@link #DES_SYMMETRIC} — replace symmetric cipher with DES.</dd>
 *   <dd>{@link #ECB_SYMMETRIC} — replace AES/CBC with insecure AES/ECB mode.</dd>
 *   <dd>{@link #MD5_JAVA} — replace hash algorithm with MD5 (Java standard).</dd>
 *   <dd>{@link #MD5_BOUNCY_CASTLE} — replace hash algorithm with MD5 (BouncyCastle).</dd>
 *   <dd>{@link #WEAK_PRNG} — replace {@code SecureRandom} with {@code Random}.</dd>
 *
 *   <dt>Input validation bypass</dt>
 *   <dd>{@link #PATTERN_MATCHES_ANYTHING} — replace regex pattern with one that
 *       matches any string.</dd>
 *   <dd>{@link #STRING_MATCHER_MATCHES_ANYTHING} — same for {@code String.matches}.</dd>
 *   <dd>{@link #TRUST_FILENAME_INPUT} — remove filename sanitisation, allowing
 *       path traversal.</dd>
 *
 *   <dt>Injection</dt>
 *   <dd>{@link #SQL_INJECTION_JDBC} — replace parameterised query with string
 *       concatenation.</dd>
 * </dl>
 */
public enum SecurityMutations {

    // -- Cookie flags --------------------------------------------------------
    COOKIE_HTTPONLY_DISABLE(CookieHttpOnlyFlagDisableMutator.REMOVE_HTTPONLY_FLAG_MUTATOR),
    COOKIE_SECURE_DISABLE(CookieSecureFlagDisableMutator.REMOVE_SECURE_FLAG_MUTATOR),

    // -- XML parser hardening ------------------------------------------------
    XML_DISABLE_DOCTYPE_SAX(
            DisableDOCTYPEVerificationOnXMLParserWithSAXMutator.XML_PARSER_VULNERABLE_TO_XXE_WITH_SAX),
    XML_DISABLE_DOCTYPE_XMLREADER(
            DisableDOCTYPEVerificationOnXMLParserWithXMLReaderMutator.XML_PARSER_VULNERABLE_TO_XXE_WITH_XMLREADER),
    XML_DISABLE_DOS_SAX(
            DisableDOSVerificationOnXMLParserWithSAXMutator.XML_PARSER_VULNERABLE_TO_DOS_WITH_SAX),
    XML_DISABLE_DOS_XMLREADER(
            DisableDOSVerificationOnXMLParserWithXMLReaderMutator.XML_PARSER_VULNERABLE_TO_DOS_WITH_XMLREADER),

    // -- TLS / network -------------------------------------------------------
    HOSTNAME_VERIFY_TRUE(HostNameVerifyToTrueMutator.HOST_NAME_VERIFY_TO_TRUE),
    REMOVE_SSL_SOCKET(RemoveSSLInSocketMutator.REMOVE_SECURE_SOCKET_MUTATOR),

    // -- Cryptography weakening ----------------------------------------------
    RSA_SHORT_KEY(RSAWithShortKeyMutator.RSA_WITH_SHORT_KEY_MUTATOR),
    BLOWFISH_SHORT_KEY(UseBLOWFISHWithShortKeyMutator.USE_BLOWFISH_WITH_SHORT_KEY),
    DES_SYMMETRIC(UseDESForSymmetricEncryptionMutator.USE_DES_FOR_SYMMETRIC_ENCRYPTION),
    ECB_SYMMETRIC(UseECBInSymmetricEncryptionMutator.USE_ECB_IN_SYMMETRIC_ENCRYPTION),
    MD5_JAVA(UseMD5ForEncryptionJAVAStandardMutator.USE_MD5_FOR_ENCRYPTION_JAVA_STANDARD_MUTATOR),
    MD5_BOUNCY_CASTLE(UseMD5ForEncryptionWithBouncyCastleMutator.USE_MD5_FOR_ENCRYPTION_WITH_BOUNCY_CASTLE),
    WEAK_PRNG(UseWeakPseudoRandomNumberGeneratorMutator.USE_WEAK_PSEUDO_RANDOM_NUMBER_GENERATOR_MUTATOR),

    // -- Input validation bypass ---------------------------------------------
    PATTERN_MATCHES_ANYTHING(PatternMatchesAnythingMutator.PATTERN_MATCHES_ANYTHING_MUTATOR),
    STRING_MATCHER_MATCHES_ANYTHING(StringMatcherMatchesAnythingMutator.STRING_MATCHER_MATCHES_ANYTHING_MUTATOR),
    TRUST_FILENAME_INPUT(TrustUserInputInFilesRetrievementMutator.TRUST_USER_INPUT_IN_FILES_RETRIEVEMENT),

    // -- Injection -----------------------------------------------------------
    SQL_INJECTION_JDBC(SQLInjectionOKWithJDBCMutator.SQL_INJECTION_OK_WITH_JDBC);

    // ------------------------------------------------------------------------

    private final MethodMutatorFactory factory;

    SecurityMutations(MethodMutatorFactory factory) {
        this.factory = factory;
    }

    /**
     * Returns a {@link Mutator} configured to apply this security operator,
     * using {@code classRoot} to resolve sibling classes during frame computation.
     */
    public Mutator mutator(Path classRoot) {
        return new PitestMutator(factory, classRoot);
    }
}
