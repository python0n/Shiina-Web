package dev.osunolimits.routes.ap.post;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

public class HandleReplayAction extends Shiina {

    private static final String OSR_FINAL_DIR = "/mnt/storage/osu/bancho-py-ex/.data/osr/";
    private static final String BANCHO_API = "http://127.0.0.1/v1/calculate_pp";
    private static final String API_KEY = App.env.get("BANCHO_API_KEY");

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

        long pendingId = Long.parseLong(idStr);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        ResultSet rs = shiina.mysql.Query(
                "SELECT * FROM pending_replays WHERE id = ? AND status = 0", String.valueOf(pendingId));

        if (rs == null || !rs.next()) {
            return redirect(res, shiina, "/ap/replays?error=notfound");
        }

        if ("reject".equals(action)) {
            shiina.mysql.Exec(
                    "UPDATE pending_replays SET status = 2, reviewed_at = ? WHERE id = ?",
                    now, (Long) pendingId);
            App.log.info("Replay " + pendingId + " rejected by " + shiina.user.name);
            return redirect(res, shiina, "/ap/replays?action=rejected");
        }

        if (!"approve".equals(action)) {
            return redirect(res, shiina, "/ap/replays");
        }

        // --- APPROVE ---
        int userid    = rs.getInt("userid");
        String mapMd5 = rs.getString("map_md5");
        int mode      = rs.getInt("mode");
        int mods      = rs.getInt("mods");
        int score     = rs.getInt("score");
        int maxCombo  = rs.getInt("max_combo");
        double acc    = rs.getDouble("acc");
        int n300      = rs.getInt("n300");
        int n100      = rs.getInt("n100");
        int n50       = rs.getInt("n50");
        int nmiss     = rs.getInt("nmiss");
        int ngeki     = rs.getInt("ngeki");
        int nkatu     = rs.getInt("nkatu");
        int perfect   = rs.getInt("perfect");
        String grade  = rs.getString("grade");
        String replayMd5 = rs.getString("replay_md5");
        String osrPath   = rs.getString("osr_path");

        // pobierz beatmap_id i status mapy
        ResultSet mapRs = shiina.mysql.Query("SELECT id, status FROM maps WHERE md5 = ?", mapMd5);
        int beatmapId = 0;
        int mapStatus = 0;
        if (mapRs != null && mapRs.next()) {
            beatmapId = mapRs.getInt("id");
            mapStatus = mapRs.getInt("status");
        }
        int scoreStatus = (mapStatus == 2 || mapStatus == 5) ? 2 : 1;

        // --- oblicz PP przez API bancho ---
        double pp = 0.0;
        if (beatmapId > 0) {
            try {
                String urlStr = BANCHO_API +
                        "?id=" + beatmapId +
                        "&n300=" + n300 +
                        "&ngeki=" + ngeki +
                        "&nkatu=" + nkatu +
                        "&n100=" + n100 +
                        "&n50=" + n50 +
                        "&misses=" + nmiss +
                        "&mods=" + mods +
                        "&mode=" + mode +
                        "&combo=" + maxCombo;

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                    com.google.gson.JsonElement el = JsonParser.parseReader(reader);
                    JsonObject result = el.isJsonArray() ? el.getAsJsonArray().get(0).getAsJsonObject() : el.getAsJsonObject();
                    pp = result.get("performance").getAsJsonObject().get("pp").getAsDouble();
                    if (Double.isNaN(pp) || Double.isInfinite(pp)) pp = 0.0;
                    reader.close();
                }
                conn.disconnect();
                App.log.info("PP calculated: " + pp + " for beatmap_id=" + beatmapId);
            } catch (Exception e) {
                App.log.warn("PP calculation failed: " + e.getMessage());
            }
        }

        // wstaw score z PP
        shiina.mysql.Exec(
                "INSERT INTO scores (map_md5, score, pp, acc, max_combo, mods, n300, n100, n50, nmiss, ngeki, nkatu, " +
                "grade, status, mode, play_time, time_elapsed, client_flags, userid, perfect, online_checksum) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0, 0, ?, ?, ?)",
                mapMd5,
                (Integer) score,
                (Double) pp,
                (Double) acc,
                (Integer) maxCombo,
                (Integer) mods,
                (Integer) n300,
                (Integer) n100,
                (Integer) n50,
                (Integer) nmiss,
                (Integer) ngeki,
                (Integer) nkatu,
                grade,
                (Integer) scoreStatus,
                (Integer) mode,
                (Integer) userid,
                (Integer) perfect,
                replayMd5
        );

        ResultSet newScoreRs = shiina.mysql.Query(
                "SELECT id FROM scores WHERE userid = ? AND map_md5 = ? AND online_checksum = ? ORDER BY id DESC LIMIT 1",
                String.valueOf(userid), mapMd5, replayMd5);

        long newScoreId = 0;
        if (newScoreRs != null && newScoreRs.next()) {
            newScoreId = newScoreRs.getLong("id");
        }

        if (newScoreId > 0) {
            File src = new File(osrPath);
            File dst = new File(OSR_FINAL_DIR + newScoreId + ".osr");
            if (src.exists()) {
                Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        shiina.mysql.Exec(
                "UPDATE pending_replays SET status = 1, reviewed_at = ?, score_id = ? WHERE id = ?",
                now, (Long) newScoreId, (Long) pendingId);

        App.log.info("Replay " + pendingId + " approved -> score_id=" + newScoreId + " pp=" + pp);

        return redirect(res, shiina, "/ap/replays?action=approved");
    }
}
