package dev.osunolimits.routes.api.chat;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.I18n;
import spark.Request;
import spark.Response;

/**
 * The bell in the navbar. Notifications are rows of the notifications table (bancho
 * migration v5.18.0): bancho adds replay_accepted, first_place_lost and map_status,
 * Shiina adds replay_rejected, map_comment and deletion_rejected through {@link #add}.
 * The text is built here from lang/*.properties (notif.&lt;type&gt;) with {placeholders}
 * filled from the JSON data.
 */
public final class NotificationsApi {

    private static final Gson GSON = new Gson();
    public static final int BSS_SET_ID_MIN = 1073741824;

    private NotificationsApi() {}

    /** Add a notification; errors are only logged. */
    public static void add(MySQL mysql, int userId, String type, Map<String, Object> data, String link) {
        try {
            mysql.Exec("INSERT INTO notifications (user_id, type, data, link) VALUES (?, ?, ?, ?)",
                    (Integer) userId, type, GSON.toJson(data), link == null ? "" : link);
        } catch (Exception e) {
            App.log.warn("Could not add notification " + type + " for " + userId + ": " + e.getMessage());
        }
    }

    /** "Artist - Title [Version]" of a map by md5, or the md5 itself. */
    public static String mapNameByMd5(MySQL mysql, String md5) {
        try {
            ResultSet rs = mysql.Query("SELECT artist, title, version FROM maps WHERE md5 = ?", md5);
            if (rs != null && rs.next()) {
                return rs.getString("artist") + " - " + rs.getString("title") + " [" + rs.getString("version") + "]";
            }
        } catch (Exception ignored) {
            // fall through
        }
        return md5;
    }

    static String text(Map<String, String> t, String type, JsonObject data) {
        String template = t.getOrDefault("notif." + type, type);
        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            String value = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
            if ("status".equals(e.getKey())) {
                value = t.getOrDefault("notif.status." + value, value);
            }
            if ("reason".equals(e.getKey())) {
                value = value.isBlank() ? "" : t.getOrDefault("notif.reason", "Reason: {reason}").replace("{reason}", value);
            }
            template = template.replace("{" + e.getKey() + "}", value);
        }
        return template.replaceAll("\\{[a-z_]+\\}", "").trim();
    }

    /** GET /api/v1/notifications - the newest 30 notifications and the unread count. */
    public static class ListRoute extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
            res.type("application/json");
            res.header("Cache-Control", "no-store");
            if (!shiina.loggedIn) {
                res.status(401);
                return ChatApi.json(shiina, Map.of("error", "unauthorized"));
            }
            Map<String, String> t = I18n.messages(I18n.detect(req));
            List<Map<String, Object>> items = new ArrayList<>();
            ResultSet rs = shiina.mysql.Query(
                    "SELECT id, type, data, link, UNIX_TIMESTAMP(created_at) AS ts, `read` FROM notifications "
                            + "WHERE user_id = ? ORDER BY id DESC LIMIT 30",
                    shiina.user.id);
            while (rs != null && rs.next()) {
                JsonObject data;
                try {
                    data = JsonParser.parseString(rs.getString("data")).getAsJsonObject();
                } catch (Exception e) {
                    data = new JsonObject();
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("type", rs.getString("type"));
                m.put("text", text(t, rs.getString("type"), data));
                String link = rs.getString("link");
                m.put("link", link != null && link.startsWith("/") ? link : "");
                m.put("time", rs.getLong("ts"));
                m.put("read", rs.getInt("read") == 1);
                items.add(m);
            }
            int unread = 0;
            ResultSet c = shiina.mysql.Query("SELECT COUNT(*) AS c FROM notifications WHERE user_id = ? AND `read` = 0",
                    shiina.user.id);
            if (c != null && c.next()) {
                unread = c.getInt("c");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("unread", unread);
            body.put("items", items);
            return ChatApi.json(shiina, body);
        }
    }

    /** POST /api/v1/notifications/read?id=N or ?all=1 */
    public static class ReadRoute extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
            res.type("application/json");
            if (!shiina.loggedIn) {
                res.status(401);
                return ChatApi.json(shiina, Map.of("error", "unauthorized"));
            }
            int id = ChatApi.intParam(req, "id", 0);
            if (id > 0) {
                shiina.mysql.Exec("UPDATE notifications SET `read` = 1 WHERE id = ? AND user_id = ?",
                        (Integer) id, (Integer) shiina.user.id);
            } else if ("1".equals(req.queryParams("all"))) {
                shiina.mysql.Exec("UPDATE notifications SET `read` = 1 WHERE user_id = ? AND `read` = 0",
                        (Integer) shiina.user.id);
            }
            return ChatApi.json(shiina, Map.of("ok", true));
        }
    }
}
