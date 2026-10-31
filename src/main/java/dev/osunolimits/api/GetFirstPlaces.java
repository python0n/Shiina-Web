package dev.osunolimits.routes.api.get;

import java.sql.ResultSet;
import java.util.ArrayList;

import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.MySQLRoute;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.OsuConverter;
import lombok.Data;
import spark.Request;
import spark.Response;

public class GetFirstPlaces extends MySQLRoute {

    // First places on ranked/approved/qualified/loved maps (status 2-5), the
    // same maps bancho announces #1 for. Vanilla modes rank by each player's
    // best score (status 2 or 3), rx/ap by best pp (status 2). Restricted
    // players are left out, like on the in-game leaderboard.
    private static String firstPlacesFrom(boolean byPp) {
        String metric = byPp ? "pp" : "score";
        String statuses = byPp ? "(2)" : "(2, 3)";
        return "FROM scores s " +
            "JOIN ( " +
            "  SELECT sc.map_md5, MAX(sc." + metric + ") AS best " +
            "  FROM scores sc JOIN users u ON u.id = sc.userid " +
            "  WHERE sc.status IN " + statuses + " AND sc.mode = ? AND u.priv & 1 " +
            "  GROUP BY sc.map_md5 " +
            ") AS best_scores " +
            "  ON s.map_md5 = best_scores.map_md5 " +
            " AND s." + metric + " = best_scores.best " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.status IN " + statuses + " AND s.mode = ? " +
            "  AND m.status IN (2, 3, 4, 5) ";
    }

    private static String getFirstPlacesQuery(boolean byPp, String sort) {
        return "SELECT s.id AS score_id, s.userid, s.map_md5, " +
            "m.id AS map_id, m.set_id AS map_set_id, m.filename AS map_name, " +
            "s.score AS max_score, s.pp, s.acc, s.mods, s.grade, s.play_time " +
            firstPlacesFrom(byPp) +
            "ORDER BY s.play_time " + sort + " LIMIT ? OFFSET ?;";
    }

    private static String countFirstPlacesQuery(boolean byPp) {
        return "SELECT COUNT(DISTINCT s.map_md5) AS first_place_count " + firstPlacesFrom(byPp) + ";";
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = getRequest();
        ShiinaAPIHandler shiinaAPIHandler = new ShiinaAPIHandler();

        int mode = 0;
        if (OsuConverter.checkForValidMode(req.queryParams("mode"))) {
            mode = Integer.parseInt(req.queryParams("mode"));
        } else {
            shiinaAPIHandler.addRequiredParameter("mode", "int", "missing or invalid");
        }

        Integer id = null;
        if (req.queryParams("id") != null && Validation.isNumeric(req.queryParams("id"))) {
            id = Integer.parseInt(req.queryParams("id"));
        } else {
            shiinaAPIHandler.addRequiredParameter("id", "int", "missing or invalid");
        }

        Integer offset = 0;
        if (req.queryParams("offset") != null && Validation.isNumeric(req.queryParams("offset"))) {
            offset = Integer.parseInt(req.queryParams("offset"));
        } else {
            shiinaAPIHandler.addRequiredParameter("offset", "int", "missing or invalid");
        }

        String sort = "DESC";
        if (req.queryParams("sort") != null && req.queryParams("sort").equals("asc")) {
            sort = "ASC";
        }

        if (shiinaAPIHandler.hasIssues()) {
            return shiinaAPIHandler.renderIssues(shiina, res);
        }

        ArrayList<FirstPlace> firstPlaces = new ArrayList<>();
        FirstPlacesResponse response = new FirstPlacesResponse();
        boolean hasNextPage = false;

        // Fetch 6 to detect "next page", then drop the 6th item.
        boolean byPp = mode >= 4;
        ResultSet firstPlacesQuery = shiina.mysql.Query(getFirstPlacesQuery(byPp, sort), mode, id, mode, 6, offset);
        while (firstPlacesQuery.next()) {
            FirstPlace firstPlace = new FirstPlace();
            firstPlace.setScore_id(firstPlacesQuery.getInt("score_id"));
            firstPlace.setUser_id(firstPlacesQuery.getInt("userid"));
            firstPlace.setMap_md5(firstPlacesQuery.getString("map_md5"));
            firstPlace.setMap_id(firstPlacesQuery.getInt("map_id"));
            firstPlace.setMap_set_id(firstPlacesQuery.getInt("map_set_id"));
            firstPlace.setMap_name(firstPlacesQuery.getString("map_name"));
            firstPlace.setMax_score(firstPlacesQuery.getInt("max_score"));
            firstPlace.setPp(firstPlacesQuery.getFloat("pp"));
            firstPlace.setAcc(firstPlacesQuery.getFloat("acc"));
            firstPlace.setMods(OsuConverter.convertMods(firstPlacesQuery.getInt("mods")));
            firstPlace.setGrade(firstPlacesQuery.getString("grade"));
            firstPlace.setPlay_time(firstPlacesQuery.getString("play_time"));
            firstPlaces.add(firstPlace);
        }

        if (firstPlaces.size() == 6) {
            hasNextPage = true;
            firstPlaces.remove(5);
        }

        ResultSet countQuery = shiina.mysql.Query(countFirstPlacesQuery(byPp), mode, id, mode);
        if (countQuery.next()) {
            response.setCount(countQuery.getInt("first_place_count"));
        }

        response.setFirstPlaces(firstPlaces.toArray(new FirstPlace[0]));
        response.setStatus("success");
        response.setHasNextPage(hasNextPage);

        return shiinaAPIHandler.renderJSON(response, shiina, res);
    }

    @Data
    public class FirstPlacesResponse {
        private String status;
        private boolean hasNextPage;
        private int count;
        private FirstPlace[] firstPlaces;
    }

    @Data
    public class FirstPlace {
        private int score_id;
        private int user_id;
        private String map_md5;
        private int map_id;
        private int map_set_id;
        private String map_name;
        private int max_score;
        private float pp;
        private float acc;
        private String[] mods;
        private String grade;
        private String play_time;
    }
}
