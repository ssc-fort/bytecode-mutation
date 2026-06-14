package io.mutator.samples;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
public class MessageDigestSample {
    public static MessageDigest getDigest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }
}
