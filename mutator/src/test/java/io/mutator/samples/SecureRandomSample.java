package io.mutator.samples;
import java.security.SecureRandom;
public class SecureRandomSample {
    public static byte[] generate(SecureRandom sr, int length) {
        byte[] buf = new byte[length];
        sr.nextBytes(buf);
        return buf;
    }
}
