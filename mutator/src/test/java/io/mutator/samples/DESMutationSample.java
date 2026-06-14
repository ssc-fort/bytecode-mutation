package io.mutator.samples;
import javax.crypto.Cipher;
import java.security.Key;
public class DESMutationSample {
    public static void initCipher(Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
    }
}
