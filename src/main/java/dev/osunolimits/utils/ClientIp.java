package dev.osunolimits.utils;

import spark.Request;

/**
 * The visitor's address and country. Shiina listens only on 127.0.0.1 behind nginx,
 * so req.ip() is 127.0.0.1 for everyone. nginx sets X-Real-IP (and CF-IPCountry where
 * it knows the country); both are trusted only when the request came from nginx itself.
 */
public final class ClientIp {

    private ClientIp() {
    }

    private static boolean fromProxy(Request req) {
        String remote = req.ip();
        return "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
    }

    public static String of(Request req) {
        if (fromProxy(req)) {
            String real = req.headers("X-Real-IP");
            if (real != null && !real.isBlank() && real.length() <= 45) {
                return real.trim();
            }
        }
        return req.ip();
    }

    /** Two-letter country code in lower case, or "xx" when it isn't known. */
    public static String country(Request req) {
        if (fromProxy(req)) {
            String cc = req.headers("CF-IPCountry");
            if (cc != null && cc.trim().matches("[A-Za-z]{2}") && !cc.trim().equalsIgnoreCase("XX")
                    && !cc.trim().equalsIgnoreCase("T1")) {
                return cc.trim().toLowerCase();
            }
        }
        return "xx";
    }
}
