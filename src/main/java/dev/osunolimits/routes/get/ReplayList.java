package dev.osunolimits.routes.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;
import dev.osunolimits.main.App;

public class ReplayList extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/replays");
        }

        ResultSet rs = shiina.mysql.Query(
                "SELECT pr.id, pr.map_md5, pr.mode, pr.mods, pr.score, pr.max_combo, pr.acc, " +
                "pr.grade, pr.status, pr.submitted_at, pr.reviewed_at, " +
                "m.title, m.artist, m.version " +
                "FROM pending_replays pr " +
                "LEFT JOIN maps m ON m.md5 = pr.map_md5 " +
                "WHERE pr.userid = ? " +
                "ORDER BY pr.submitted_at DESC",
                shiina.user.id);

        List<Map<String, Object>> replays = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", rs.getLong("id"));
            r.put("map_md5", rs.getString("map_md5"));
            r.put("mode", rs.getInt("mode"));
            r.put("mods", rs.getInt("mods"));
            r.put("score", rs.getInt("score"));
            r.put("max_combo", rs.getInt("max_combo"));
            r.put("acc", String.format("%.2f", rs.getFloat("acc")));
            r.put("grade", rs.getString("grade"));
            r.put("status", rs.getInt("status")); // 0=pending,1=approved,2=rejected
            r.put("submitted_at", rs.getString("submitted_at"));
            r.put("reviewed_at", rs.getString("reviewed_at"));
            r.put("title", rs.getString("title"));
            r.put("artist", rs.getString("artist"));
            r.put("version", rs.getString("version"));
            replays.add(r);
        }

        shiina.data.put("replays", replays);
        shiina.data.put("submitted", req.queryParams("submitted") != null);

        shiina.data.put("seo", new dev.osunolimits.modules.utils.SEOBuilder("Replay Submit", "Prześlij replay do weryfikacji"));
        return renderTemplate("replays.html", shiina, res, req);
    }
}
