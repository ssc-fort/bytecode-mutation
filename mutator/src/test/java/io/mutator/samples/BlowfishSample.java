package io.mutator.samples;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
public class BlowfishSample {
    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator gen = KeyGenerator.getInstance("Blowfish");
        gen.init(256);
        return gen.generateKey();
    }
}
