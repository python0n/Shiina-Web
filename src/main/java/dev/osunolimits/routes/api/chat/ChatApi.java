package dev.osunolimits.routes.api.chat;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

/**
 * JSON endpoints of the website chat (/chat).
 *
 * The chat itself runs over bancho's /ws/chat (IRC over WebSocket). These routes give
 * the browser a one-time login ticket, the PM history (bancho keeps every PM in the
 * mail table), the last messages of public channels (bancho keeps them in Redis) and
 * the number of unread PMs for the navbar icon.
 */
public final class ChatApi {

    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PAGE = 50;
    static final int BOT_ID = 1;

    private ChatApi() {}

    /** Logged in and not restricted, or null after setting the error response. */
    static ShiinaRequest auth(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        res.type("application/json");
        if (!shiina.loggedIn) {
            res.status(401);
            close(shiina);
            return null;
        }
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.UNRESTRICTED)) {
            res.status(403);
            close(shiina);
            return null;
        }
        return shiina;
    }

    static void close(ShiinaRequest shiina) {
        if (shiina != null && shiina.mysql != null) {
            shiina.mysql.close();
        }
    }

    static String json(ShiinaRequest shiina, Object body) {
        close(shiina);
        return GSON.toJson(body);
    }

    static String error(Response res, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        return GSON.toJson(m);
    }

    static int intParam(Request req, String name, int def) {
        String v = req.queryParams(name);
        if (v == null || !v.matches("\\d{1,10}")) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static String safeName(String name) {
        return name == null ? "" : name.trim().toLowerCase().replace(' ', '_');
    }

    /** POST /api/v1/chat/ticket - one-time login for /ws/chat, valid for 60 seconds. */
    public static class Ticket extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            App.jedisPool.setex("chat:ticket:" + ticket, 60, String.valueOf(shiina.user.id));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ticket", ticket);
            m.put("id", shiina.user.id);
            m.put("name", shiina.user.name);
            return json(shiina, m);
        }
    }

    /** GET /api/v1/chat/conversations - people the player exchanged PMs with, newest first. */
    public static class Conversations extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            int me = shiina.user.id;
            ResultSet rs = shiina.mysql.Query(
                    "SELECT c.peer, MAX(c.last_id) AS last_id, MAX(c.last_time) AS last_time, "
                            + "SUM(c.unread) AS unread, u.name, u.priv "
                            + "FROM ("
                            + " SELECT to_id AS peer, MAX(id) AS last_id, MAX(time) AS last_time, 0 AS unread "
                            + " FROM mail WHERE from_id = ? GROUP BY to_id"
                            + " UNION ALL "
                            + " SELECT from_id AS peer, MAX(id) AS last_id, MAX(time) AS last_time, "
                            + " SUM(`read` = 0) AS unread FROM mail WHERE to_id = ? GROUP BY from_id"
                            + ") c JOIN users u ON u.id = c.peer "
                            + "WHERE c.peer <> ? "
                            + "GROUP BY c.peer, u.name, u.priv ORDER BY last_id DESC LIMIT 60",
                    me, me, me);

            List<Map<String, Object>> list = new ArrayList<>();
            while (rs != null && rs.next()) {
                // restricted players are hidden from others, like everywhere on the site
                if ((rs.getInt("priv") & 1) == 0) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("peer"));
                m.put("name", rs.getString("name"));
                m.put("time", rs.getLong("last_time"));
                m.put("unread", rs.getInt("unread"));
                list.add(m);
            }
            return json(shiina, list);
        }
    }

    /** GET /api/v1/chat/history?user=ID[&before=MAIL_ID] - PMs with one player, oldest first. */
    public static class History extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            int me = shiina.user.id;
            int peer = intParam(req, "user", 0);
            int before = intParam(req, "before", Integer.MAX_VALUE);
            if (peer <= 0) {
                res.status(400);
                close(shiina);
                return error(res, "user");
            }
            ResultSet rs = shiina.mysql.Query(
                    "SELECT id, from_id, to_id, msg, time FROM mail "
                            + "WHERE ((from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)) AND id < ? "
                            + "ORDER BY id DESC LIMIT " + (PAGE + 1),
                    me, peer, peer, me, before);

            List<Map<String, Object>> list = new ArrayList<>();
            while (rs != null && rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("from_id", rs.getInt("from_id"));
                m.put("msg", rs.getString("msg"));
                m.put("time", rs.getLong("time"));
                list.add(m);
            }
            boolean more = list.size() > PAGE;
            if (more) {
                list.remove(list.size() - 1);
            }
            Collections.reverse(list);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", list);
            body.put("more", more);
            return json(shiina, body);
        }
    }

    /** POST /api/v1/chat/read?user=ID - the conversation was opened on the website. */
    public static class Read extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            int peer = intParam(req, "user", 0);
            if (peer > 0) {
                shiina.mysql.Exec("UPDATE mail SET `read` = 1 WHERE to_id = ? AND from_id = ? AND `read` = 0",
                        (Integer) shiina.user.id, (Integer) peer);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            return json(shiina, m);
        }
    }

    /** GET /api/v1/chat/unread - unread PMs, for the chat icon in the navbar. */
    public static class Unread extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
            res.type("application/json");
            res.header("Cache-Control", "no-store");
            int count = 0;
            if (shiina.loggedIn) {
                ResultSet rs = shiina.mysql.Query(
                        "SELECT COUNT(*) AS c FROM mail m JOIN users u ON u.id = m.from_id "
                                + "WHERE m.to_id = ? AND m.`read` = 0 AND u.priv & 1",
                        shiina.user.id);
                if (rs != null && rs.next()) {
                    count = rs.getInt("c");
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("unread", count);
            return json(shiina, m);
        }
    }

    /** GET /api/v1/chat/channel?name=#osu - last messages of a public channel, oldest first. */
    public static class ChannelHistory extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            close(shiina);
            String name = req.queryParams("name");
            List<Object> list = new ArrayList<>();
            if (name != null && name.matches("#[A-Za-z0-9_#\\-]{1,32}")) {
                List<String> raw = App.jedisPool.lrange("chat:history:" + name, 0, PAGE - 1);
                for (int i = raw.size() - 1; i >= 0; i--) {
                    try {
                        JsonObject o = JsonParser.parseString(raw.get(i)).getAsJsonObject();
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("from", o.get("from").getAsString());
                        m.put("from_id", o.get("from_id").getAsInt());
                        m.put("msg", o.get("msg").getAsString());
                        m.put("time", o.get("time").getAsLong());
                        list.add(m);
                    } catch (Exception ignored) {
                        // skip broken entries
                    }
                }
            }
            return GSON.toJson(list);
        }
    }

    /** GET /api/v1/chat/users?names=a,b - ids of chat nicks (IRC uses names, avatars need ids). */
    public static class Users extends Shiina {
        @Override
        public Object handle(Request req, Response res) throws Exception {
            ShiinaRequest shiina = auth(req, res);
            if (shiina == null) {
                return error(res, "unauthorized");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            String names = req.queryParams("names");
            if (names != null) {
                int n = 0;
                for (String raw : names.split(",")) {
                    String safe = safeName(raw);
                    if (safe.isEmpty() || safe.length() > 32 || out.containsKey(safe) || n++ >= 40) {
                        continue;
                    }
                    ResultSet rs = shiina.mysql.Query(
                            "SELECT id, name, priv FROM users WHERE safe_name = ?", safe);
                    if (rs != null && rs.next() && ((rs.getInt("priv") & 1) != 0 || rs.getInt("id") == shiina.user.id)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", rs.getInt("id"));
                        m.put("name", rs.getString("name"));
                        out.put(safe, m);
                    }
                }
            }
            return json(shiina, out);
        }
    }
}
