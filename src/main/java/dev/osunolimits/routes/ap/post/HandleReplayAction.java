package dev.osunolimits.routes.ap.post;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import spark.Request;
import spark.Response;

/**
 * Accept or reject a replay uploaded on the website.
 *
 * Accepting is done by bancho (POST /v1/approve_replay, X-Internal-Key): it saves
 * the score the same way as a score sent from the game (Relax/Autopilot mode, pp,
 * best score status, player stats, rank), moves the .osr file and marks the
 * pending row as accepted.
 */
public class HandleReplayAction extends Shiina {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn || shiina.user.id != 3) {
            res.status(403);
            return "Forbidden";
        }

        String action = req.queryParams("action");
        String idStr  = req.queryParams("id");

        if (idStr == null || action == null) {
            return redirect(res, shiina, "/ap/replays");
        }

        long pendingId;
        try {
            pendingId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return redirect(res, shiina, "/ap/replays");
        }

        ResultSet rs = shiina.mysql.Query(
                "SELECT id FROM pending_replays WHERE id = ? AND status = 0", String.valueOf(pendingId));

        if (rs == null || !rs.next()) {
            return redirect(res, shiina, "/ap/replays?error=not_found");
        }

        if ("reject".equals(action)) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            shiina.mysql.Exec(
                    "UPDATE pending_replays SET status = 2, reviewed_at = ? WHERE id = ?",
                    now, (Long) pendingId);
            App.log.info("Replay " + pendingId + " rejected by " + shiina.user.name);
            return redirect(res, shiina, "/ap/replays?action=rejected");
        }

        if (!"approve".equals(action)) {
            return redirect(res, shiina, "/ap/replays");
        }

        String apiUrl = App.env.get("APIURL");
        String internalKey = App.env.get("INTERNAL_API_KEY");
        if (apiUrl == null || internalKey == null || internalKey.isBlank()) {
            App.log.warn("Replay approval needs APIURL and INTERNAL_API_KEY in .env");
            return redirect(res, shiina, "/ap/replays?error=config");
        }

        okhttp3.Request apiReq = new okhttp3.Request.Builder()
                .url(apiUrl + "/v1/approve_replay?id=" + pendingId)
                .header("X-Internal-Key", internalKey)
                .post(RequestBody.create(new byte[0]))
                .build();

        try (okhttp3.Response apiRes = CLIENT.newCall(apiReq).execute()) {
            String body = apiRes.body() != null ? apiRes.body().string() : "";
            JsonObject json = null;
            try {
                json = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception ignored) {
                // not json, handled below
            }

            if (apiRes.code() == 200 && json != null && json.has("score_id")) {
                long scoreId = json.get("score_id").getAsLong();
                String pp = json.has("pp") ? json.get("pp").getAsString() : "0";
                App.log.info("Replay " + pendingId + " approved by " + shiina.user.name
                        + " -> score_id=" + scoreId + " pp=" + pp);
                return redirect(res, shiina, "/ap/replays?action=approved&score=" + scoreId);
            }

            String code = json != null && json.has("code") ? json.get("code").getAsString() : "bancho";
            App.log.warn("Replay " + pendingId + " approval failed (" + apiRes.code() + "): " + body);
            if (!code.matches("[a-z_]{1,32}")) code = "bancho";
            return redirect(res, shiina, "/ap/replays?error=" + code);
        } catch (Exception e) {
            App.log.warn("Replay " + pendingId + " approval: bancho not reachable: " + e.getMessage());
            return redirect(res, shiina, "/ap/replays?error=bancho");
        }
    }
}
