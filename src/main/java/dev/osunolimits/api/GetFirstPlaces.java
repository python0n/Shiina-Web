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

    // Show only first places on maps that are NOT unranked.
    // Assumption based on your DB: maps.status = 0 => unranked.
    private static final String GET_FIRST_PLACES =
            "SELECT s.id AS score_id, s.userid, s.map_md5, " +
            "m.id AS map_id, m.set_id AS map_set_id, m.filename AS map_name, " +
            "s.score AS max_score, s.pp, s.acc, s.mods, s.grade, s.play_time " +
            "FROM scores s " +
            "JOIN ( " +
            "  SELECT map_md5, mode, MAX(score) AS max_score " +
            "  FROM scores " +
            "  WHERE status = 2 " +
            "  GROUP BY map_md5, mode " +
            ") AS max_scores " +
            "  ON s.map_md5 = max_scores.map_md5 " +
            " AND s.mode = max_scores.mode " +
            " AND s.score = max_scores.max_score " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.status = 2 AND s.mode = ? " +
            "  AND m.status <> 0 " +
            "ORDER BY s.play_time DESC LIMIT ? OFFSET ?;";

    private static final String COUNT_FIRST_PLACES =
            "SELECT COUNT(DISTINCT s.map_md5) AS first_place_count " +
            "FROM scores s " +
            "JOIN ( " +
            "  SELECT map_md5, mode, MAX(score) AS max_score " +
            "  FROM scores " +
            "  WHERE status = 2 " +
            "  GROUP BY map_md5, mode " +
            ") AS max_scores " +
            "  ON s.map_md5 = max_scores.map_md5 " +
            " AND s.mode = max_scores.mode " +
            " AND s.score = max_scores.max_score " +
            "JOIN maps m ON s.map_md5 = m.md5 " +
            "WHERE s.userid = ? AND s.status = 2 AND s.mode = ? " +
            "  AND m.status <> 0;";

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
        String finalQuery = GET_FIRST_PLACES.replace("ORDER BY s.play_time DESC", "ORDER BY s.play_time " + sort);
        ResultSet firstPlacesQuery = shiina.mysql.Query(finalQuery, id, mode, 6, offset);
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

        ResultSet countQuery = shiina.mysql.Query(COUNT_FIRST_PLACES, id, mode);
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
