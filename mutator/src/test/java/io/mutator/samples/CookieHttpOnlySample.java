package io.mutator.samples;
import javax.servlet.http.Cookie;
public class CookieHttpOnlySample {
    public static void secure(Cookie cookie) {
        cookie.setHttpOnly(true);
    }
}
