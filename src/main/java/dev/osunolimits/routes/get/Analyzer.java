package dev.osunolimits.routes.get;

import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

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

    private static final Gson JSON = new Gson();

    private static final Logger log = LoggerFactory.getLogger(Analyzer.class);
    private static final String OSR_PATH = dev.osunolimits.utils.BanchoData.replays();
    private static final String OSU_PATH = dev.osunolimits.utils.BanchoData.beatmaps();

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
        boolean showFailed = "1".equals(req.queryParams("failed"));
        shiina.data.put("showFailed", showFailed);
        ResultSet rs = shiina.mysql.Query(
            "SELECT s.id, s.score, s.acc, s.grade, s.pp, s.mods, s.play_time, s.max_combo, s.n100, s.n50, s.nmiss, " +
            "m.title, m.artist, m.version, m.diff, m.set_id, m.id as map_id " +
            "FROM scores s " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.mode = 0 " + (showFailed ? "" : "AND s.status > 0 ") +
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
            score.put("mods_str", modString(rs.getInt("mods")));
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
        // JSON for the search box; Gson also escapes < and > so a map title cannot end the <script> it sits in
        List<Map<String, Object>> forJson = new ArrayList<>();
        for (Map<String, Object> s : scores) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", s.get("id"));
            o.put("title", String.valueOf(s.get("title")));
            o.put("artist", String.valueOf(s.get("artist")));
            o.put("version", String.valueOf(s.get("version")));
            o.put("diff", String.valueOf(s.get("diff")));
            o.put("acc", String.valueOf(s.get("acc")));
            o.put("pp", String.valueOf(s.get("pp")));
            o.put("grade", String.valueOf(s.get("grade")));
            o.put("n100", s.get("n100"));
            o.put("n50", s.get("n50"));
            o.put("nmiss", s.get("nmiss"));
            o.put("play_time", String.valueOf(s.get("play_time")));
            o.put("set_id", s.get("set_id"));
            o.put("hasReplay", s.get("hasReplay"));
            forJson.add(o);
        }
        String json = JSON.toJson(forJson);
        shiina.data.put("scoresJson", json);
        shiina.data.put("seo", new SEOBuilder("Analyzer", "Analiza replayów"));
        return renderTemplate("analyzer/list.html", shiina, res, req);
    }

    private Object handleSearch(Request req, Response res, ShiinaRequest shiina) throws Exception {
        String q = req.queryParams("q");
        if (q == null || q.trim().length() < 2) {
            res.type("application/json");
            return "[]";
        }
        String like = "%" + q.trim() + "%";
        boolean showFailed = "1".equals(req.queryParams("failed"));
        ResultSet rs = shiina.mysql.Query(
            "SELECT s.id, s.score, s.acc, s.grade, s.pp, s.mods, s.play_time, s.n100, s.n50, s.nmiss, " +
            "m.id AS map_id, m.title, m.artist, m.version, m.diff, m.set_id " +
            "FROM scores s " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.mode = 0 " + (showFailed ? "" : "AND s.status > 0 ") +
            "AND (m.title LIKE ? OR m.artist LIKE ? OR m.version LIKE ?) " +
            "ORDER BY s.id DESC LIMIT 30",
            shiina.user.id, like, like, like
        );
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs != null && rs.next()) {
            int scoreId = rs.getInt("id");
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", scoreId);
            o.put("title", rs.getString("title"));
            o.put("artist", rs.getString("artist"));
            o.put("version", rs.getString("version"));
            o.put("diff", String.format(java.util.Locale.US, "%.2f", rs.getFloat("diff")));
            o.put("acc", String.format(java.util.Locale.US, "%.2f", rs.getFloat("acc")));
            o.put("pp", String.format(java.util.Locale.US, "%.0f", rs.getFloat("pp")));
            o.put("grade", rs.getString("grade"));
            o.put("n100", rs.getInt("n100"));
            o.put("n50", rs.getInt("n50"));
            o.put("nmiss", rs.getInt("nmiss"));
            o.put("play_time", rs.getString("play_time"));
            o.put("set_id", rs.getInt("set_id"));
            o.put("map_id", rs.getInt("map_id"));
            o.put("mods", rs.getInt("mods"));
            o.put("mods_str", modString(rs.getInt("mods")));
            o.put("hasReplay", new java.io.File(OSR_PATH + scoreId + ".osr").exists());
            results.add(o);
        }
        res.type("application/json");
        return JSON.toJson(results);
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
        score.put("mods_str", modString(rs.getInt("mods")));
        score.put("mods", rs.getInt("mods"));
        shiina.data.put("score", score);
        shiina.data.put("scoreId", scoreId);

        File osrFile = new File(OSR_PATH + scoreId + ".osr");
        File osuFile = new File(OSU_PATH + mapId + ".osu");

        if (!osrFile.exists()) {
            shiina.data.put("error", "Replay file is not available for this score.");
            shiina.data.put("seo", new SEOBuilder("Analyzer", ""));
            return renderTemplate("analyzer/view.html", shiina, res, req);
        }

        if (!osuFile.exists()) {
            shiina.data.put("error", "Beatmap file is not available locally. Play this map again to download it.");
            shiina.data.put("seo", new SEOBuilder("Analyzer", ""));
            return renderTemplate("analyzer/view.html", shiina, res, req);
        }

        try {
            byte[] osrBytes = Files.readAllBytes(osrFile.toPath());
            OsrData osr = OsrParser.parse(osrBytes, true);
            OsuData osu = OsuFileParser.parse(osuFile.getAbsolutePath());
            int nmiss = rs.getInt("nmiss");
            AnalysisResult analysis = ReplayAnalyzer.analyze(osr, osu, nmiss, rs.getInt("mods"));

            List<Map<String, Object>> recommendedMaps = getRecommendedMaps(shiina, analysis, mapId, diff);
            analysis.setRecommendedMaps(recommendedMaps);

            shiina.data.put("analysis", analysis);
            shiina.data.put("noteResultsJson", buildNoteResultsJson(analysis));
        } catch (Exception e) {
            log.error("Failed to analyze replay {}: {}", scoreId, e.getMessage(), e);
            // the details stay in the log: the message of an I/O error holds server paths
            shiina.data.put("error", "This replay could not be analyzed.");
        }

        shiina.data.put("seo", new SEOBuilder("Analyzer", ""));
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
                    int tl2 = rs2.getInt("total_length");
                    float nps2 = tl2 > 0 ? (float) rs2.getInt("max_combo") / tl2 : 0;
                    m.put("skillset", classifySkillset(nps2, rs2.getFloat("ar")));
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to get recommended maps: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static String modString(int mods) {
        int[][] order = {{1, 0}, {2, 1}, {8, 2}, {16, 3}, {512, 4}, {64, 5}, {256, 6}, {1024, 7}, {32, 8}, {16384, 9}, {4096, 10}};
        String[] names = {"NF", "EZ", "HD", "HR", "NC", "DT", "HT", "FL", "SD", "PF", "SO"};
        StringBuilder sb = new StringBuilder();
        for (int[] m : order) {
            if ((mods & m[0]) == 0) continue;
            if (m[0] == 64 && (mods & 512) != 0) continue;
            if (m[0] == 32 && (mods & 16384) != 0) continue;
            sb.append(names[m[1]]);
        }
        return sb.toString();
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
        List<Map<String, Object>> out = new ArrayList<>();
        for (var n : analysis.getNoteResults()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("t", n.getNoteTimeMs());
            o.put("te", Math.round(n.getTimingError() * 10.0) / 10.0);
            o.put("ae", Math.round(n.getAimError() * 10.0) / 10.0);
            o.put("sl", n.isSlider());
            o.put("v", String.valueOf(n.getVerdict()));
            o.put("s", n.isInStream());
            o.put("j", n.isInJump());
            out.add(o);
        }
        return JSON.toJson(out);
    }
}
