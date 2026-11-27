package dev.osunolimits.routes.get;

import dev.osunolimits.api.ScoreQuery;
import dev.osunolimits.api.ScoreQuery.Score;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.Embed;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.modules.osr.BeatmapMedia;
import dev.osunolimits.modules.osr.OsrParser;
import dev.osunolimits.modules.osr.OsuFileParser;
import dev.osunolimits.modules.osr.ReplayAnalyzer;
import dev.osunolimits.modules.osr.ReplayPlayerData;
import dev.osunolimits.utils.BanchoData;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.Visibility;
import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;

public class UserScore extends Shiina {

    private static final Logger log = LoggerFactory.getLogger(UserScore.class);


    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        Integer id = null;
        if (req.params("id") != null && Validation.isNumeric(req.params("id"))) {
            id = Integer.parseInt(req.params("id"));
        }

        if(id == null) {
            return notFound(res, shiina);
        }

        ScoreQuery scoreQuery = new ScoreQuery(shiina.mysql);
        Score s = scoreQuery.getScore(id);
        if(s == null || !Visibility.canSee(shiina, s.getUserId())) {
            return notFound(res, shiina);
        }

        // replay player (osu!standard, also relax and autopilot): data and media for /js/replay-player.js
        ResultSet mrs = shiina.mysql.Query(
            "SELECT s.mods, s.mode, m.id AS map_id, m.set_id, m.mode AS map_mode FROM scores s JOIN maps m ON m.md5 = s.map_md5 WHERE s.id = ?", id);
        int mods = 0, mode = -1, mapId = 0, setId = 0, mapMode = -1;
        if (mrs != null && mrs.next()) {
            mods = mrs.getInt("mods");
            mode = mrs.getInt("mode");
            mapId = mrs.getInt("map_id");
            setId = mrs.getInt("set_id");
            mapMode = mrs.getInt("map_mode");
        }
        File osrFile = new File(BanchoData.replays() + id + ".osr");
        File osuFile = new File(BanchoData.beatmaps() + mapId + ".osu");
        boolean canWatch = (mode == 0 || mode == 4 || mode == 8) && mapMode == 0 && osrFile.exists() && osuFile.exists();
        String media = req.queryParams("media");
        if ("1".equals(req.queryParams("replay")) || media != null) {
            if (!canWatch) {
                res.status(404);
                return "";
            }
            if ("audio".equals(media) || "bg".equals(media)) {
                java.nio.file.Path file = BeatmapMedia.get(setId, osuFile.getAbsolutePath(), media, App.env.get("ANALYZER_OSZ_URL"));
                if (file == null) {
                    res.status(404);
                    return "";
                }
                return BeatmapMedia.serve(req, res, file);
            }
            try {
                OsrParser.OsrData osr = OsrParser.parse(Files.readAllBytes(osrFile.toPath()), true);
                OsuFileParser.OsuData osu = OsuFileParser.parse(osuFile.getAbsolutePath());
                // verdicts for the player; with Relax the analyzer clicks where the game would
                ReplayAnalyzer.AnalysisResult analysis = ReplayAnalyzer.analyze(osr, osu, 0, mods);
                res.type("application/json");
                return ReplayPlayerData.json(osr, osu, mods, analysis);
            } catch (Exception e) {
                log.error("Failed to build replay data {}: {}", id, e.getMessage(), e);
                res.status(500);
                res.type("application/json");
                return "{}";
            }
        }
        shiina.data.put("canWatch", canWatch);

        shiina.data.put("score", s);
        if (shiina.loggedIn && shiina.user.id == s.getUserId()) {
            shiina.data.put("canPin", true);
            java.sql.ResultSet pinRs = shiina.mysql.Query("SELECT pinned FROM scores WHERE id = ?", id);
            if (pinRs.next()) {
                shiina.data.put("scorePinned", pinRs.getInt("pinned") == 1);
            }
        }

        if (s.getBeatmap().getFilename() != null) {
            String seoMap = s.getBeatmap().getArtist() + " - " + s.getBeatmap().getTitle() + " [" + s.getBeatmap().getVersion() + "]";
            String seoDesc = Embed.text(shiina, "seo.score", "pp", Math.round(s.getPp()), "acc", Embed.decimal(s.getAcc(), 2),
                    "grade", Embed.grade(s.getGrade()), "mods", Embed.mods(s.getMods()), "combo", s.getMaxCombo(),
                    "mode", Embed.mode(s.getMode()));
            String seoImage = "https://assets.ppy.sh/beatmaps/" + s.getBeatmap().getSet_id() + "/covers/cover.jpg";
            shiina.data.put("seo", new SEOBuilder(s.getUsername() + " - " + seoMap, seoDesc, seoImage));
        }
        
        return renderTemplate("score.html", shiina, res, req);
    }
    
}
