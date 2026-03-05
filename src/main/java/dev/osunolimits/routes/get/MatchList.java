package dev.osunolimits.routes.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

public class MatchList extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        ResultSet rs = shiina.mysql.Query(
            "SELECT m.id, m.name, m.created_at, m.ended_at, u.name as creator_name, " +
            "(SELECT COUNT(*) FROM mp_match_games WHERE match_id = m.id) as game_count " +
            "FROM mp_matches m LEFT JOIN users u ON u.id = m.creator_id " +
            "ORDER BY m.created_at DESC LIMIT 50"
        );

        List<Map<String, Object>> matches = new ArrayList<>();
        while (rs != null && rs.next()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("creator_name", rs.getString("creator_name"));
            m.put("created_at", rs.getString("created_at"));
            m.put("ended_at", rs.getString("ended_at"));
            m.put("game_count", rs.getInt("game_count"));
            matches.add(m);
        }

        shiina.data.put("matches", matches);
        shiina.data.put("seo", new SEOBuilder("Match List", ""));
        return renderTemplate("matches.html", shiina, res, req);
    }
}
