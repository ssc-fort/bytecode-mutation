package io.mutator.samples;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
public class BouncyCastleSample {
    public static PKCS5S2ParametersGenerator createGenerator() {
        return new PKCS5S2ParametersGenerator(new SHA256Digest());
    }
}
