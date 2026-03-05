package dev.osunolimits.routes.get;

import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.modules.osr.OsrParser;
import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsuFileParser;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;
import spark.Request;
import spark.Response;

public class Analyzer extends Shiina {

    private static final Logger log = LoggerFactory.getLogger(Analyzer.class);
    private static final String OSR_PATH = "/mnt/storage/osu/bancho-py-ex/.data/osr/";
    private static final String OSU_PATH = "/mnt/storage/osu/bancho-py-ex/.data/osu/";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/analyzer");
        }

        String scoreIdParam = req.params(":score_id");
        if ("search".equals(scoreIdParam)) {
            return handleSearch(req, res, shiina);
        } else if (scoreIdParam == null) {
            return handleList(req, res, shiina);
        } else {
            return handleAnalysis(req, res, shiina, scoreIdParam);
        }
    }

    private Object handleList(Request req, Response res, ShiinaRequest shiina) throws Exception {
        ResultSet rs = shiina.mysql.Query(
            "SELECT s.id, s.score, s.acc, s.grade, s.pp, s.mods, s.play_time, s.max_combo, s.n100, s.n50, s.nmiss, " +
            "m.title, m.artist, m.version, m.diff, m.set_id, m.id as map_id " +
            "FROM scores s " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.mode = 0 " +
            "ORDER BY s.id DESC LIMIT 50",
            shiina.user.id
        );

        List<Map<String, Object>> scores = new ArrayList<>();
        while (rs != null && rs.next()) {
            Map<String, Object> score = new HashMap<>();
            int scoreId = rs.getInt("id");
            score.put("id", scoreId);
            score.put("score", rs.getInt("score"));
            score.put("acc", String.format("%.2f", rs.getFloat("acc")));
            score.put("grade", rs.getString("grade"));
            score.put("pp", String.format("%.0f", rs.getFloat("pp")));
            score.put("mods", rs.getInt("mods"));
            score.put("max_combo", rs.getInt("max_combo"));
            score.put("play_time", rs.getString("play_time"));
            score.put("title", rs.getString("title"));
            score.put("artist", rs.getString("artist"));
            score.put("version", rs.getString("version"));
            score.put("diff", String.format("%.2f", rs.getFloat("diff")));
            score.put("set_id", rs.getInt("set_id"));
            score.put("map_id", rs.getInt("map_id"));
            score.put("n100", rs.getInt("n100"));
            score.put("n50", rs.getInt("n50"));
            score.put("nmiss", rs.getInt("nmiss"));
            score.put("hasReplay", new File(OSR_PATH + scoreId + ".osr").exists());
            scores.add(score);
        }

        shiina.data.put("scores", scores);
        // JSON dla wyszukiwarki
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < scores.size(); i++) {
            Map<String,Object> s = scores.get(i);
            if (i > 0) json.append(",");
            json.append(String.format(java.util.Locale.US,
                "{\"id\":%d,\"title\":\"%s\",\"artist\":\"%s\",\"version\":\"%s\",\"diff\":\"%s\",\"acc\":\"%s\",\"pp\":\"%s\",\"grade\":\"%s\",\"n100\":%d,\"n50\":%d,\"nmiss\":%d,\"play_time\":\"%s\",\"set_id\":%d,\"hasReplay\":%b}",
                (int)s.get("id"),
                s.get("title").toString().replace("\"","\\\""),
                s.get("artist").toString().replace("\"","\\\""),
                s.get("version").toString().replace("\"","\\\""),
                s.get("diff"), s.get("acc"), s.get("pp"), s.get("grade"),
                (int)s.get("n100"), (int)s.get("n50"), (int)s.get("nmiss"),
                s.get("play_time"), (int)s.get("set_id"), (boolean)s.get("hasReplay")
            ));
        }
        json.append("]");
        shiina.data.put("scoresJson", json.toString());
        shiina.data.put("seo", new SEOBuilder("Analyzer | " + App.customization.get("serverName"), "Analiza replayów"));
        return renderTemplate("analyzer/list.html", shiina, res, req);
    }

    private Object handleSearch(Request req, Response res, ShiinaRequest shiina) throws Exception {
        String q = req.queryParams("q");
        if (q == null || q.trim().length() < 2) {
            res.type("application/json");
            return "[]";
        }
        String like = "%" + q.trim() + "%";
        ResultSet rs = shiina.mysql.Query(
            "SELECT s.id, s.score, s.acc, s.grade, s.pp, s.play_time, s.n100, s.n50, s.nmiss, " +
            "m.title, m.artist, m.version, m.diff, m.set_id " +
            "FROM scores s " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.mode = 0 " +
            "AND (m.title LIKE ? OR m.artist LIKE ? OR m.version LIKE ?) " +
            "ORDER BY s.id DESC LIMIT 30",
            shiina.user.id, like, like, like
        );
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs != null && rs.next()) {
            int scoreId = rs.getInt("id");
            boolean hasReplay = new java.io.File(OSR_PATH + scoreId + ".osr").exists();
            String title = rs.getString("title").replace("\\", "\\\\").replace("\"", "\\\"");
            String artist = rs.getString("artist").replace("\\", "\\\\").replace("\"", "\\\"");
            String version = rs.getString("version").replace("\\", "\\\\").replace("\"", "\\\"");
            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"id\":").append(scoreId).append(",");
            json.append("\"title\":\"").append(title).append("\",");
            json.append("\"artist\":\"").append(artist).append("\",");
            json.append("\"version\":\"").append(version).append("\",");
            json.append("\"diff\":\"").append(String.format(java.util.Locale.US, "%.2f", rs.getFloat("diff"))).append("\",");
            json.append("\"acc\":\"").append(String.format(java.util.Locale.US, "%.2f", rs.getFloat("acc"))).append("\",");
            json.append("\"pp\":\"").append(String.format(java.util.Locale.US, "%.0f", rs.getFloat("pp"))).append("\",");
            json.append("\"grade\":\"").append(rs.getString("grade")).append("\",");
            json.append("\"n100\":").append(rs.getInt("n100")).append(",");
            json.append("\"n50\":").append(rs.getInt("n50")).append(",");
            json.append("\"nmiss\":").append(rs.getInt("nmiss")).append(",");
            json.append("\"play_time\":\"").append(rs.getString("play_time")).append("\",");
            json.append("\"set_id\":").append(rs.getInt("set_id")).append(",");
            json.append("\"hasReplay\":").append(hasReplay);
            json.append("}");
        }
        json.append("]");
        res.type("application/json");
        return json.toString();
    }


    private Object handleAnalysis(Request req, Response res, ShiinaRequest shiina, String scoreIdParam) throws Exception {
        int scoreId;
        try {
            scoreId = Integer.parseInt(scoreIdParam);
        } catch (NumberFormatException e) {
            return redirect(res, shiina, "/analyzer");
        }

        ResultSet rs = shiina.mysql.Query(
            "SELECT s.id, s.userid, s.map_md5, s.acc, s.score, s.grade, s.pp, s.mods, s.max_combo, s.nmiss, s.n300, s.n100, s.n50, " +
            "m.id as map_id, m.title, m.artist, m.version, m.diff, m.set_id, m.od, m.cs " +
            "FROM scores s " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.id = ?",
            scoreId
        );

        if (rs == null || !rs.next()) return redirect(res, shiina, "/analyzer");
        if (rs.getInt("userid") != shiina.user.id) return redirect(res, shiina, "/analyzer");

        int mapId = rs.getInt("map_id");
        float diff = rs.getFloat("diff");
        Map<String, Object> score = new HashMap<>();
        score.put("id", scoreId);
        score.put("title", rs.getString("title"));
        score.put("artist", rs.getString("artist"));
        score.put("version", rs.getString("version"));
        score.put("diff", String.format("%.2f", diff));
        score.put("set_id", rs.getInt("set_id"));
        score.put("map_id", mapId);
        score.put("acc", String.format("%.2f", rs.getFloat("acc")));
        score.put("grade", rs.getString("grade"));
        score.put("pp", String.format("%.0f", rs.getFloat("pp")));
        score.put("score", rs.getInt("score"));
        score.put("max_combo", rs.getInt("max_combo"));
        score.put("nmiss", rs.getInt("nmiss"));
        score.put("n300", rs.getInt("n300"));
        score.put("n100", rs.getInt("n100"));
        score.put("n50", rs.getInt("n50"));
        shiina.data.put("score", score);
        shiina.data.put("scoreId", scoreId);

        File osrFile = new File(OSR_PATH + scoreId + ".osr");
        File osuFile = new File(OSU_PATH + mapId + ".osu");

        if (!osrFile.exists()) {
            shiina.data.put("error", "Replay file is not available for this score.");
            shiina.data.put("seo", new SEOBuilder("Analyzer | " + App.customization.get("serverName"), ""));
            return renderTemplate("analyzer/view.html", shiina, res, req);
        }

        if (!osuFile.exists()) {
            shiina.data.put("error", "Beatmap file is not available locally. Play this map again to download it.");
            shiina.data.put("seo", new SEOBuilder("Analyzer | " + App.customization.get("serverName"), ""));
            return renderTemplate("analyzer/view.html", shiina, res, req);
        }

        try {
            byte[] osrBytes = Files.readAllBytes(osrFile.toPath());
            OsrData osr = OsrParser.parse(osrBytes, true);
            OsuData osu = OsuFileParser.parse(osuFile.getAbsolutePath());
            int nmiss = rs.getInt("nmiss");
            float cs = rs.getFloat("cs");
            AnalysisResult analysis = ReplayAnalyzer.analyze(osr, osu, nmiss, cs);

            List<Map<String, Object>> recommendedMaps = getRecommendedMaps(shiina, analysis, mapId, diff);
            analysis.setRecommendedMaps(recommendedMaps);

            shiina.data.put("analysis", analysis);
            shiina.data.put("noteResultsJson", buildNoteResultsJson(analysis));
        } catch (Exception e) {
            log.error("Failed to analyze replay {}: {}", scoreId, e.getMessage(), e);
            shiina.data.put("error", "Analysis error: " + e.getMessage());
        }

        shiina.data.put("seo", new SEOBuilder("Analyzer | " + App.customization.get("serverName"), ""));
        return renderTemplate("analyzer/view.html", shiina, res, req);
    }

    private List<Map<String, Object>> getRecommendedMaps(ShiinaRequest shiina, AnalysisResult analysis, int currentMapId, float currentDiff) {
        float minDiff = Math.max(0, currentDiff - 0.7f);
        float maxDiff = currentDiff + 0.7f;
        try {
            // pobierz skillset aktualnej mapy
            ResultSet cur = shiina.mysql.Query(
                "SELECT bpm, ar, max_combo, total_length FROM maps WHERE id = ? AND mode = 0 LIMIT 1", currentMapId);
            float curBpm = 0; float curAr = 0; float curNps = 0;
            if (cur != null && cur.next()) {
                curBpm = cur.getFloat("bpm");
                curAr = cur.getFloat("ar");
                int mc = cur.getInt("max_combo");
                int tl = cur.getInt("total_length");
                curNps = tl > 0 ? (float) mc / tl : 0;
            }

            // klasyfikuj aktualną mapę
            String curSkillset = classifySkillset(curNps, curAr);

            // zdecyduj jaki skillset rekomendować
            // domyślnie: ten sam skillset co aktualna mapa
            // nadpisz tylko jeśli gracz ma SPECYFICZNE słabości skillsetowe
            List<String> wk = analysis.getWeaknesses();
            String targetSkillset = curSkillset;
            if (wk.contains("weak_streams")) targetSkillset = "stream";
            else if (wk.contains("weak_jumps")) targetSkillset = "jump";

            // zbuduj warunek SQL dla skillsetu
            String skillsetCondition = buildSkillsetCondition(targetSkillset);

            String sql = String.format(java.util.Locale.US,
                "SELECT m.id, m.title, m.artist, m.version, m.diff, m.set_id, m.bpm, m.ar, m.max_combo, m.total_length " +
                "FROM maps m " +
                "WHERE m.id != %d AND m.mode = 0 " +
                "AND m.diff BETWEEN %.3f AND %.3f " +
                "AND m.status IN (2,5) " +
                "AND (%s) " +
                "AND m.md5 NOT IN (SELECT map_md5 FROM scores WHERE userid = %d) " +
                "ORDER BY RAND() LIMIT 5",
                currentMapId, minDiff, maxDiff, skillsetCondition, shiina.user.id
            );
            ResultSet rs = shiina.mysql.Query(sql);
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs != null && rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("title", rs.getString("title"));
                m.put("artist", rs.getString("artist"));
                m.put("version", rs.getString("version"));
                m.put("diff", String.format("%.2f", rs.getFloat("diff")));
                m.put("set_id", rs.getInt("set_id"));
                m.put("bpm", String.format("%.0f", rs.getFloat("bpm")));
                float nps = rs.getInt("total_length") > 0 ? (float)rs.getInt("max_combo") / rs.getInt("total_length") : 0;
                m.put("skillset", classifySkillset(nps, rs.getFloat("ar")));
                result.add(m);
            }
            // fallback — jeśli brak wyników ze skillsetem, daj RAND po diff
            if (result.isEmpty()) {
                String sqlFallback = String.format(java.util.Locale.US,
                    "SELECT m.id, m.title, m.artist, m.version, m.diff, m.set_id, m.bpm, m.ar, m.max_combo, m.total_length " +
                    "FROM maps m WHERE m.id != %d AND m.mode = 0 " +
                    "AND m.diff BETWEEN %.3f AND %.3f AND m.status IN (2,5) " +
                    "AND m.md5 NOT IN (SELECT map_md5 FROM scores WHERE userid = %d) " +
                    "ORDER BY RAND() LIMIT 5",
                    currentMapId, minDiff, maxDiff, shiina.user.id
                );
                ResultSet rs2 = shiina.mysql.Query(sqlFallback);
                while (rs2 != null && rs2.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs2.getInt("id"));
                    m.put("title", rs2.getString("title"));
                    m.put("artist", rs2.getString("artist"));
                    m.put("version", rs2.getString("version"));
                    m.put("diff", String.format("%.2f", rs2.getFloat("diff")));
                    m.put("set_id", rs2.getInt("set_id"));
                    m.put("bpm", String.format("%.0f", rs2.getFloat("bpm")));
                    m.put("skillset", classifySkillset(rs2.getFloat("bpm"), rs2.getFloat("ar")));
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to get recommended maps: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private String classifySkillset(float nps, float ar) {
        if (nps >= 8.0f) return "stream";
        if (ar >= 9.0f) return "jump";
        if (nps >= 6.0f) return "burst";
        return "tech";
    }

    private String buildSkillsetCondition(String skillset) {
        switch (skillset) {
            case "stream": return "(m.max_combo / m.total_length) >= 8.0";
            case "jump":   return "m.ar >= 9.0 AND (m.max_combo / m.total_length) < 8.0";
            case "burst":  return "(m.max_combo / m.total_length) >= 6.0 AND (m.max_combo / m.total_length) < 8.0 AND m.ar < 9.0";
            default:       return "(m.max_combo / m.total_length) < 6.0 AND m.ar < 9.0";
        }
    }


    private String buildNoteResultsJson(AnalysisResult analysis) {
        StringBuilder sb = new StringBuilder("[");
        var notes = analysis.getNoteResults();
        for (int i = 0; i < notes.size(); i++) {
            var n = notes.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                .append("\"t\":").append(n.getNoteTimeMs()).append(",")
                .append("\"te\":").append(String.format("%.1f", n.getTimingError())).append(",")
                .append("\"ae\":").append(String.format("%.1f", n.getAimError())).append(",")
                .append("\"v\":\"").append(n.getVerdict()).append("\",")
                .append("\"s\":").append(n.isInStream()).append(",")
                .append("\"j\":").append(n.isInJump())
                .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
