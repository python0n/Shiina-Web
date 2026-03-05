package dev.osunolimits.routes.get;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import okhttp3.Request;
import okhttp3.Response;

/**
 * /matches/:id
 * Renders LIVE/HISTORY view for multiplayer matches using mp_matches table.
 */
public class MultiplayerMatch extends Shiina {

    private final Gson gson = new Gson();

    @Override
    public Object handle(spark.Request req, spark.Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String idStr = req.params("id");
        if (idStr == null || !idStr.matches("\\d+")) {
            return notFound(res, shiina);
        }

        int webId = Integer.parseInt(idStr);

        ResultSet rs = shiina.mysql.Query(
                "SELECT id, bancho_slot, name, creator_id, created_at, ended_at FROM mp_matches WHERE id = ? LIMIT 1;",
                webId);

        if (!rs.next()) {
            return notFound(res, shiina);
        }

        int banchoSlot = rs.getInt("bancho_slot");
        String name = rs.getString("name");
        int creatorId = rs.getInt("creator_id");

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp endedAtTs = rs.getTimestamp("ended_at");

        Long createdAt = createdAtTs != null ? createdAtTs.getTime() / 1000L : null;
        Long endedAt = endedAtTs != null ? endedAtTs.getTime() / 1000L : null;

        String creatorName = null;
        try {
            ResultSet cr = shiina.mysql.Query("SELECT name FROM users WHERE id = ? LIMIT 1;", creatorId);
            if (cr.next()) {
                creatorName = cr.getString("name");
            }
        } catch (Exception ignored) {
        }

        shiina.data.put("webId", webId);
        shiina.data.put("banchoSlot", banchoSlot);
        shiina.data.put("dbName", name);
        shiina.data.put("creatorId", creatorId);
        shiina.data.put("creatorName", creatorName);
        shiina.data.put("createdAt", createdAt);
        shiina.data.put("endedAt", endedAt);

        boolean isLive = endedAt == null;
        shiina.data.put("isLive", isLive);

        if (isLive) {
            String apiBase = App.env.get("APIURL");
            if (apiBase == null || apiBase.isBlank()) {
                apiBase = App.env.get("APIURLPUBLIC");
            }
            if (apiBase != null && !apiBase.isBlank()) {
                String url = apiBase.replaceAll("/+$", "") + "/v1/get_match?id=" + banchoSlot;

                Request httpReq = new Request.Builder().url(url).get().build();
                try (Response httpRes = App.sharedClient.newCall(httpReq).execute()) {
                    if (httpRes.isSuccessful() && httpRes.body() != null) {
                        String body = httpRes.body().string();
                        Map<String, Object> root = gson.fromJson(body, new TypeToken<Map<String, Object>>() {
                        }.getType());
                        Object matchObj = root != null ? root.get("match") : null;
                        if (matchObj instanceof Map) {
                            shiina.data.put("liveMatch", matchObj);
                        }
                        shiina.data.put("liveRawStatus", root != null ? root.get("status") : null);
                    } else {
                        shiina.data.put("liveError", "API request failed (" + httpRes.code() + ")");
                    }
                } catch (Exception e) {
                    shiina.data.put("liveError", "API request error: " + e.getMessage());
                }
            } else {
                shiina.data.put("liveError", "APIURL is not configured");
            }
        }

        return renderTemplate("match.html", shiina, res, req);
    }
}
