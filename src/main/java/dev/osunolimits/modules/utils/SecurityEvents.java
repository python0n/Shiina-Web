package dev.osunolimits.modules.utils;

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;

/**
 * Account security events for the staff channel on Discord. The bot listens on the
 * Redis channel "ex:security" and posts one line per event. Sending never breaks the
 * action itself: if Redis is down, the event is only logged.
 */
public final class SecurityEvents {

    private static final Gson GSON = new Gson();

    private SecurityEvents() {
    }

    public static String nameOf(MySQL mysql, int userId) {
        try {
            ResultSet rs = mysql.Query("SELECT `name` FROM `users` WHERE `id` = ?", userId);
            if (rs != null && rs.next()) {
                return rs.getString("name");
            }
        } catch (Exception e) {
            // only for the message
        }
        return String.valueOf(userId);
    }

    /**
     * @param action  event type, e.g. "password_reset", "recovery_link", "staff_action"
     * @param userId  the account the event is about
     * @param byId    who did it (0 when it was the player himself)
     * @param what    short description for staff actions, may be null
     */
    public static void publish(MySQL mysql, String action, int userId, int byId, String what) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("action", action);
            msg.put("user_id", userId);
            msg.put("name", nameOf(mysql, userId));
            if (byId > 0) {
                msg.put("by_id", byId);
                msg.put("by_name", nameOf(mysql, byId));
            }
            if (what != null) {
                msg.put("what", what.length() > 200 ? what.substring(0, 200) : what);
            }
            App.jedisPool.publish("ex:security", GSON.toJson(msg));
        } catch (Exception e) {
            App.log.warn("Could not publish security event " + action + ": " + e.getMessage());
        }
    }
}
