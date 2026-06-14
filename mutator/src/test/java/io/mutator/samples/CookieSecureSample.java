package io.mutator.samples;
import javax.servlet.http.Cookie;
public class CookieSecureSample {
    public static void secure(Cookie cookie) {
        cookie.setSecure(true);
    }
}
