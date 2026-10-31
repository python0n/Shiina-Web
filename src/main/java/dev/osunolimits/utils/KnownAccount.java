package dev.osunolimits.utils;

import java.sql.ResultSet;

import dev.osunolimits.common.MySQL;
import spark.Request;
import spark.Response;

/**
 * Remembers in the browser which account was last used on it (a long-lived
 * cookie that stays after logging out), and finds accounts that played from
 * the same IP. Used to warn before someone makes a second account.
 */
public final class KnownAccount {

    private static final String COOKIE = "tsg_known";
    private static final int TWO_YEARS = 2 * 365 * 24 * 60 * 60;

    private KnownAccount() {}

    public static void remember(Response res, int userId) {
        res.cookie("/", COOKIE, String.valueOf(userId), TWO_YEARS, true, true);
    }

    /** Account id stored in this browser, if the account still exists. */
    public static Integer fromCookie(Request req, MySQL mysql) {
        String raw = req.cookie(COOKIE);
        if (raw == null || !Validation.isNumeric(raw)) {
            return null;
        }
        int id = Integer.parseInt(raw);
        try (ResultSet rs = mysql.Query("SELECT `id` FROM `users` WHERE `id` = ?", id)) {
            return rs != null && rs.next() ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The client's real IP behind nginx / Cloudflare. */
    public static String clientIp(Request req) {
        String ip = req.headers("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            String forwarded = req.headers("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                ip = forwarded.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = req.ip();
        }
        return ip;
    }

    /** Most recent account that logged in from this IP in the game in the last 60 days. */
    public static Integer fromIp(Request req, MySQL mysql) {
        String ip = clientIp(req);
        if (ip == null || ip.startsWith("127.") || ip.equals("::1")) {
            return null;
        }
        try (ResultSet rs = mysql.Query(
                "SELECT `userid` FROM `ingame_logins` WHERE `ip` = ? AND `datetime` > NOW() - INTERVAL 60 DAY "
                        + "ORDER BY `datetime` DESC LIMIT 1", ip)) {
            return rs != null && rs.next() ? rs.getInt("userid") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String nameOf(MySQL mysql, int userId) {
        try (ResultSet rs = mysql.Query("SELECT `name` FROM `users` WHERE `id` = ?", userId)) {
            return rs != null && rs.next() ? rs.getString("name") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
