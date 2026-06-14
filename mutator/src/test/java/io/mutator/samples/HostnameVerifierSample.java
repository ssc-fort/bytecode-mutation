package io.mutator.samples;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
public class HostnameVerifierSample {
    public static boolean callVerify(HostnameVerifier verifier, String hostname, SSLSession session) {
        return verifier.verify(hostname, session);
    }
}
