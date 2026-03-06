package dev.osunolimits.utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCrypt;

import com.google.gson.annotations.SerializedName;

import dev.osunolimits.main.App;
import lombok.Data;

public class Auth {

    @Data
    public static class User {
        public Integer id;
        public String name;
        public String safe_name;
        public String email;
        public Integer priv;
        public Integer created;
    }

    @Data
    public static class SessionUser {
        @SerializedName("id")
        public Integer id;
        @SerializedName("created")
        public Integer created;
        @SerializedName("ip")
        public String ip;
        @SerializedName("userAgent")
        public String userAgent = "Unknown";
        @SerializedName("city")
        public String city = null;
        @SerializedName("country")
        public String country = null;
    }

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder();

    public static String generateNewToken() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    /**
     * Result of a lazy-migration password check.
     * If needsRehash is true, the DB should be updated with newHash.
     */
    public static class PwCheckResult {
        public final boolean matched;
        public final boolean needsRehash;
        public final String newHash;

        PwCheckResult(boolean matched, boolean needsRehash, String newHash) {
            this.matched = matched;
            this.needsRehash = needsRehash;
            this.newHash = newHash;
        }
    }

    /**
     * Lazy migration password check.
     * 1. Try bcrypt(raw) directly – new format (no MD5).
     * 2. If that fails, try bcrypt(md5(raw)) – old format.
     *    On match: signals that the hash should be re-stored without MD5.
     */
    public static PwCheckResult checkPwLazy(String raw, String bcrypt) {
        try {
            // New format first (no MD5)
            if (BCrypt.checkpw(raw, bcrypt)) {
                return new PwCheckResult(true, false, null);
            }
            // Old format fallback (MD5 + bcrypt) – lazy migration
            String md5Hash = md5(raw);
            if (md5Hash != null && BCrypt.checkpw(md5Hash, bcrypt)) {
                return new PwCheckResult(true, true, Auth.bcrypt(raw));
            }
            return new PwCheckResult(false, false, null);
        } catch (Exception e) {
            App.log.error("Failed to check password", e);
            return new PwCheckResult(false, false, null);
        }
    }

    /** @deprecated Use checkPwLazy for new code. Kept for compatibility. */
    @Deprecated
    public static Boolean checkPw(String raw, String bcrypt) {
        return checkPwLazy(raw, bcrypt).matched;
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            App.log.error("Failed to hash using MD5", e);
            return null;
        }
    }

    public static String bcrypt(String input) {
        return BCrypt.hashpw(input, BCrypt.gensalt());
    }
}
