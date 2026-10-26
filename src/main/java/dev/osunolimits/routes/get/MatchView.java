package dev.osunolimits.routes.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;
import dev.osunolimits.utils.osu.LegacyScoreCalc;
import dev.osunolimits.utils.osu.OsuConverter;

public class MatchView extends Shiina {
    private static final int MOD_SCOREV2 = 536870912;

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String idStr = req.params(":id");
        if (idStr == null || !idStr.matches("\\d+")) return redirect(res, shiina, "/matches");
        long matchId = Long.parseLong(idStr);

        ResultSet matchRs = shiina.mysql.Query(
            "SELECT m.id, m.name, m.created_at, m.ended_at, u.name as creator_name, m.creator_id " +
            "FROM mp_matches m LEFT JOIN users u ON u.id = m.creator_id " +
            "WHERE m.id = ?", matchId
        );
        if (matchRs == null || !matchRs.next()) return notFound(res, shiina);

        Map<String, Object> match = new HashMap<>();
        match.put("id", matchRs.getLong("id"));
        match.put("name", matchRs.getString("name"));
        match.put("creator_name", matchRs.getString("creator_name"));
        match.put("creator_id", matchRs.getInt("creator_id"));
        match.put("created_at", matchRs.getString("created_at"));
        match.put("ended_at", matchRs.getString("ended_at"));
        shiina.data.put("match", match);
        shiina.data.put("isLive", matchRs.getString("ended_at") == null);

        // eventy
        ResultSet eventsRs = shiina.mysql.Query(
            "SELECT e.type, e.user_id, e.data, e.created_at, u.name as username " +
            "FROM mp_match_events e LEFT JOIN users u ON u.id = e.user_id " +
            "WHERE e.match_id = ? ORDER BY e.created_at ASC", matchId
        );
        List<Map<String, Object>> events = new ArrayList<>();
        while (eventsRs != null && eventsRs.next()) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", eventsRs.getString("type"));
            ev.put("user_id", eventsRs.getInt("user_id"));
            ev.put("username", eventsRs.getString("username"));
            ev.put("data", eventsRs.getString("data"));
            ev.put("created_at", eventsRs.getString("created_at"));
            events.add(ev);
        }
        shiina.data.put("events", events);

        // gry
        ResultSet gamesRs = shiina.mysql.Query(
            "SELECT g.id, g.map_id, g.map_md5, g.mode, g.scoring_type, g.team_type, " +
            "g.mods, g.started_at, g.ended_at, mp.title, mp.artist, mp.version, mp.set_id, mp.diff " +
            "FROM mp_match_games g LEFT JOIN maps mp ON mp.md5 = g.map_md5 " +
            "WHERE g.match_id = ? AND g.ended_at IS NOT NULL ORDER BY g.started_at ASC", matchId
        );

        List<Map<String, Object>> games = new ArrayList<>();
        while (gamesRs != null && gamesRs.next()) {
            Map<String, Object> game = new HashMap<>();
            long gameId = gamesRs.getLong("id");
            game.put("id", gameId);
            game.put("map_id", gamesRs.getInt("map_id"));
            game.put("set_id", gamesRs.getInt("set_id"));
            game.put("mode", gamesRs.getInt("mode"));
            game.put("started_at", gamesRs.getString("started_at"));
            game.put("ended_at", gamesRs.getString("ended_at"));
            game.put("title", gamesRs.getString("title") != null ? gamesRs.getString("title") : "Unknown");
            game.put("artist", gamesRs.getString("artist") != null ? gamesRs.getString("artist") : "");
            game.put("version", gamesRs.getString("version") != null ? gamesRs.getString("version") : "");
            game.put("diff", gamesRs.getFloat("diff"));

            int gameMode = gamesRs.getInt("mode");
            int scoringType = gamesRs.getInt("scoring_type");
            int gameMods = gamesRs.getInt("mods");
            // the osu! client adds ScoreV2 itself when the win condition is Score V2
            if (scoringType == LegacyScoreCalc.SCORING_SCOREV2) {
                gameMods |= MOD_SCOREV2;
            }

            ResultSet scoresRs = shiina.mysql.Query(
                "SELECT s.user_id, s.score, s.max_combo, s.mods, s.n300, s.n100, s.n50, " +
                "s.nmiss, s.ngeki, s.nkatu, s.passed, s.team, u.name as username " +
                "FROM mp_match_scores s LEFT JOIN users u ON u.id = s.user_id " +
                "WHERE s.game_id = ?", gameId
            );

            List<Map<String, Object>> scores = new ArrayList<>();
            while (scoresRs != null && scoresRs.next()) {
                int n300 = scoresRs.getInt("n300");
                int n100 = scoresRs.getInt("n100");
                int n50 = scoresRs.getInt("n50");
                int geki = scoresRs.getInt("ngeki");
                int katu = scoresRs.getInt("nkatu");
                int nmiss = scoresRs.getInt("nmiss");
                // osu-web shows enabled_mods | game.mods
                int mods = scoresRs.getInt("mods") | gameMods;
                boolean passed = LegacyScoreCalc.effectivePass(scoresRs.getInt("passed") == 1, mods);
                double acc = LegacyScoreCalc.accuracy(gameMode, scoringType, n300, n100, n50, geki, katu, nmiss);

                Map<String, Object> score = new HashMap<>();
                score.put("user_id", scoresRs.getInt("user_id"));
                score.put("username", scoresRs.getString("username"));
                score.put("score", scoresRs.getLong("score"));
                score.put("acc_value", acc);
                score.put("acc", String.format(Locale.ROOT, "%.2f", acc * 100));
                score.put("max_combo", scoresRs.getInt("max_combo"));
                score.put("mods", OsuConverter.convertMods(mods));
                score.put("n300", n300);
                score.put("n100", n100);
                score.put("n50", n50);
                score.put("nmiss", nmiss);
                score.put("grade", LegacyScoreCalc.grade(gameMode, scoringType, n300, n100, n50, geki, katu, nmiss, passed, mods));
                score.put("passed", passed);
                score.put("team", scoresRs.getInt("team"));
                scores.add(score);
            }

            // failed players go to the bottom; the rest by the match win condition
            Comparator<Map<String, Object>> byWin;
            if (scoringType == 1) {
                byWin = Comparator.comparingDouble(s -> (Double) s.get("acc_value"));
            } else if (scoringType == 2) {
                byWin = Comparator.comparingInt(s -> (Integer) s.get("max_combo"));
            } else {
                byWin = Comparator.comparingLong(s -> (Long) s.get("score"));
            }
            scores.sort(Comparator.<Map<String, Object>, Boolean>comparing(s -> (Boolean) s.get("passed"))
                    .reversed()
                    .thenComparing(byWin.reversed()));
            game.put("scores", scores);
            games.add(game);
        }
        shiina.data.put("games", games);

        shiina.data.put("seo", new SEOBuilder(match.get("name").toString(), ""));
        return renderTemplate("match.html", shiina, res, req);
    }
}
