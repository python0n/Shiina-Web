package dev.osunolimits.routes.api.get;

import java.sql.ResultSet;
import java.util.ArrayList;

import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.MySQLRoute;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.OsuConverter;
import dev.osunolimits.utils.Visibility;
import lombok.Data;
import spark.Request;
import spark.Response;

public class GetPlaycountGraph extends MySQLRoute {

    private final String GET_PLAYCOUNT_GRAPH = "WITH last_12_months AS ( SELECT DATE_SUB(CURDATE(), INTERVAL (seq - 1) MONTH) AS month_date FROM ( SELECT @row := @row + 1 AS seq FROM (SELECT @row := 0) r, information_schema.columns LIMIT 12 ) AS months ) SELECT DATE_FORMAT(l.month_date, '%b %Y') AS date, COALESCE(COUNT(s.id), 0) AS plays FROM last_12_months l LEFT JOIN `scores` s ON MONTH(s.play_time) = MONTH(l.month_date) AND YEAR(s.play_time) = YEAR(l.month_date) AND s.userid = ? AND s.mode = ? GROUP BY l.month_date ORDER BY l.month_date;";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = getRequest();
        ShiinaAPIHandler shiinaAPIHandler = new ShiinaAPIHandler();

        int mode = 0;
        if (OsuConverter.checkForValidMode(req.queryParams("mode"))) {
            mode = Integer.parseInt(req.queryParams("mode"));
        }else {
            shiinaAPIHandler.addRequiredParameter("mode", "int", "missing or invalid");
        }

        Integer id = null;
        if (req.queryParams("id") != null && Validation.isNumeric(req.queryParams("id"))) {
            id = Integer.parseInt(req.queryParams("id"));
        }else {
            shiinaAPIHandler.addRequiredParameter("id", "int", "missing");
        }

        if (shiinaAPIHandler.hasIssues()) {
            return shiinaAPIHandler.renderIssues(shiina, res);
        }

        // restricted players are hidden from everyone but themselves and the staff
        if (!Visibility.canSee(shiina, id)) {
            return notFound(res, shiina);
        }

        ResultSet playcountGraphResultSet = shiina.mysql.Query(GET_PLAYCOUNT_GRAPH, id, mode);
        
        ArrayList<PlaycountGraphEntry> playcountGraphEntries = new ArrayList<>();
        while (playcountGraphResultSet.next()) {
            PlaycountGraphEntry playcountGraphEntry = new PlaycountGraphEntry();
            playcountGraphEntry.setDate(playcountGraphResultSet.getString("date"));
            playcountGraphEntry.setPlays(playcountGraphResultSet.getInt("plays"));
            playcountGraphEntries.add(playcountGraphEntry);
        }

        return shiinaAPIHandler.renderJSON(playcountGraphEntries, shiina, res);
    }

    @Data
    public class PlaycountGraphEntry {
        private String date;
        private int plays;
    }

}
