package dev.osunolimits.routes.ap.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class ApReplayList extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn || shiina.user.id != 3) {
            res.status(403);
            shiina.data.put("seo", new dev.osunolimits.modules.utils.SEOBuilder("403", ""));
            return renderTemplate("errors/forbidden.html", shiina, res, req);
        }

        String filter = req.queryParamOrDefault("status", "0"); // domyślnie pending

        ResultSet rs = shiina.mysql.Query(
                "SELECT pr.id, pr.userid, pr.map_md5, pr.player_name, pr.mode, pr.mods, pr.score, " +
                "pr.max_combo, pr.acc, pr.grade, pr.status, pr.submitted_at, pr.reviewed_at, pr.osr_path, " +
                "m.title, m.artist, m.version, " +
                "u.name as username " +
                "FROM pending_replays pr " +
                "LEFT JOIN maps m ON m.md5 = pr.map_md5 " +
                "LEFT JOIN users u ON u.id = pr.userid " +
                "WHERE pr.status = ? " +
                "ORDER BY pr.submitted_at DESC",
                Integer.parseInt(filter));

        List<Map<String, Object>> replays = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", rs.getLong("id"));
            r.put("userid", rs.getInt("userid"));
            r.put("username", rs.getString("username"));
            r.put("map_md5", rs.getString("map_md5"));
            r.put("player_name", rs.getString("player_name"));
            r.put("mode", rs.getInt("mode"));
            r.put("mods", rs.getInt("mods"));
            r.put("score", rs.getInt("score"));
            r.put("max_combo", rs.getInt("max_combo"));
            r.put("acc", String.format("%.2f", rs.getFloat("acc")));
            r.put("grade", rs.getString("grade"));
            r.put("status", rs.getInt("status"));
            r.put("submitted_at", rs.getString("submitted_at"));
            r.put("reviewed_at", rs.getString("reviewed_at"));
            r.put("osr_path", rs.getString("osr_path"));
            r.put("title", rs.getString("title"));
            r.put("artist", rs.getString("artist"));
            r.put("version", rs.getString("version"));
            replays.add(r);
        }

        shiina.data.put("replays", replays);
        shiina.data.put("filterStatus", filter);

        return renderTemplate("ap/replays.html", shiina, res, req);
    }
}
