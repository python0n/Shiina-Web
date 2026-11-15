package dev.osunolimits.modules.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.HexFormat;

import dev.osunolimits.common.MySQL;

/**
 * One-time links for setting a new password. The link carries a random token,
 * the database keeps only its SHA-256, so a database dump can't be used to reset
 * anyone's password. A token works once and only until it expires.
 */
public final class PasswordReset {

    /** links sent by e-mail */
    public static final int EMAIL_TTL_SECONDS = 30 * 60;
    /** links made by an admin in the panel */
    public static final int ADMIN_TTL_SECONDS = 24 * 60 * 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordReset() {
    }

    private static int now() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates a token for the user and returns it; only its hash is stored. */
    public static String create(MySQL mysql, int userId, int ttlSeconds) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int now = now();
        mysql.Exec("DELETE FROM `sh_password_reset` WHERE `expires_at` < ?", now);
        mysql.Exec("INSERT INTO `sh_password_reset` (`token_hash`, `user_id`, `created_at`, `expires_at`) VALUES (?, ?, ?, ?)",
                hash(token), userId, now, now + ttlSeconds);
        return token;
    }

    /** The user the token belongs to, or -1 when it is unknown or expired. */
    public static int findUser(MySQL mysql, String token) throws Exception {
        if (token == null || token.isEmpty() || token.length() > 100) {
            return -1;
        }
        ResultSet rs = mysql.Query("SELECT `user_id` FROM `sh_password_reset` WHERE `token_hash` = ? AND `expires_at` >= ?",
                hash(token), now());
        if (rs != null && rs.next()) {
            return rs.getInt("user_id");
        }
        return -1;
    }

    /** Seconds since the newest token of this user was made, or -1 when there is none. */
    public static int secondsSinceLast(MySQL mysql, int userId) throws Exception {
        ResultSet rs = mysql.Query("SELECT MAX(`created_at`) AS `last` FROM `sh_password_reset` WHERE `user_id` = ? AND `expires_at` >= ?",
                userId, now());
        if (rs != null && rs.next()) {
            int last = rs.getInt("last");
            if (!rs.wasNull() && last > 0) {
                return now() - last;
            }
        }
        return -1;
    }

    /** After a successful change every link of the user stops working. */
    public static void consumeAll(MySQL mysql, int userId) {
        mysql.Exec("DELETE FROM `sh_password_reset` WHERE `user_id` = ?", userId);
    }
}
