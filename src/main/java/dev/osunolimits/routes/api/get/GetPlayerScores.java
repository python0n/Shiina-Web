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

public class GetPlayerScores extends MySQLRoute {

    private final String GET_PLAYER_SCORES_RECENT =
        "SELECT s.id AS score_id, s.userid, s.map_md5, m.id AS map_id, " +
        "m.set_id AS map_set_id, m.filename AS map_name, s.score AS max_score, " +
        "s.pp, s.acc, s.mods, s.grade, s.play_time, s.pinned " +
        "FROM scores s " +
        "JOIN maps m ON s.map_md5 = m.md5 " +
        "WHERE s.userid = ? AND s.mode = ? " +
        "ORDER BY s.play_time DESC LIMIT ? OFFSET ?;";

    private final String GET_PLAYER_SCORES_BEST =
        "SELECT s.id AS score_id, s.userid, s.map_md5, m.id AS map_id, " +
        "m.set_id AS map_set_id, m.filename AS map_name, s.score AS max_score, " +
        "s.pp, s.acc, s.mods, s.grade, s.play_time, s.pinned " +
        "FROM scores s " +
        "JOIN maps m ON s.map_md5 = m.md5 " +
        "WHERE s.userid = ? AND s.mode = ? AND s.status = 2 AND m.status = 2 " + 
        "ORDER BY s.pp DESC LIMIT ? OFFSET ?;";
    

    private final String GET_PLAYER_SCORES_PINNED =
        "SELECT s.id AS score_id, s.userid, s.map_md5, m.id AS map_id, " +
        "m.set_id AS map_set_id, m.filename AS map_name, s.score AS max_score, " +
        "s.pp, s.acc, s.mods, s.grade, s.play_time, s.pinned " +
        "FROM scores s " +
        "JOIN maps m ON s.map_md5 = m.md5 " +
        "WHERE s.userid = ? AND s.mode = ? AND s.pinned = 1 " +
        "ORDER BY s.pin_order ASC, s.pp DESC LIMIT ? OFFSET ?;";
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = getRequest();
        ShiinaAPIHandler shiinaAPIHandler = new ShiinaAPIHandler();

        String scope = req.queryParams("scope");
        if(scope == null) {
            shiinaAPIHandler.addRequiredParameter("scope", "string", "missing");
        }

        if(scope == null || (!scope.equals("recent") && !scope.equals("best") && !scope.equals("pinned"))) {
            shiinaAPIHandler.addRequiredParameter("scope", "string", "invalid");
        }

        int mode = req.queryParams("mode") != null && Validation.isNumeric(req.queryParams("mode"))
                ? Integer.parseInt(req.queryParams("mode"))
                : 0;

        Integer id = req.queryParams("id") != null && Validation.isNumeric(req.queryParams("id"))
                ? Integer.parseInt(req.queryParams("id"))
                : null;

        Integer offset = req.queryParams("offset") != null && Validation.isNumeric(req.queryParams("offset"))
                ? Integer.parseInt(req.queryParams("offset"))
                : 0;

        Integer limit = req.queryParams("limit") != null && Validation.isNumeric(req.queryParams("limit"))
                ? Integer.parseInt(req.queryParams("limit"))
                : 5;

        if (id == null) {
            shiinaAPIHandler.addRequiredParameter("id", "int", "missing");
        }

        if (shiinaAPIHandler.hasIssues()) {
            return shiinaAPIHandler.renderIssues(shiina, res);
        }

        String getScoresQuery = scope.equals("recent") ? GET_PLAYER_SCORES_RECENT : (scope.equals("pinned") ? GET_PLAYER_SCORES_PINNED : GET_PLAYER_SCORES_BEST);

        ArrayList<PlayerScore> playerScores = new ArrayList<>();
        PlayerScoresResponse response = new PlayerScoresResponse();

        boolean hasNextPage = false;
        boolean isBest = scope.equals("best");
        int iteration = offset;

        ResultSet scoresQuery = shiina.mysql.Query(getScoresQuery, id, mode, limit + 1, offset);
        while (scoresQuery.next()) {
            PlayerScore score = new PlayerScore();
            if (isBest) {
                score.setWeight(Math.floor(Math.pow(0.95, iteration) * 100));
                score.setWeight_pp(Math.round(Math.pow(0.95, iteration) * scoresQuery.getFloat("pp")));
            }
            
            score.setScore_id(scoresQuery.getInt("score_id"));
            score.setUser_id(scoresQuery.getInt("userid"));
            score.setMap_md5(scoresQuery.getString("map_md5"));
            score.setMap_id(scoresQuery.getInt("map_id"));
            score.setMap_set_id(scoresQuery.getInt("map_set_id"));
            score.setMap_name(scoresQuery.getString("map_name"));
            score.setMax_score(scoresQuery.getInt("max_score"));
            score.setPp(scoresQuery.getFloat("pp"));
            score.setAcc(scoresQuery.getFloat("acc"));
            score.setMods(OsuConverter.convertMods(scoresQuery.getInt("mods")));  // Parse mods
            score.setGrade(scoresQuery.getString("grade"));
            score.setPlay_time(scoresQuery.getString("play_time"));
            score.setPinned(scoresQuery.getInt("pinned"));
            playerScores.add(score);
            iteration++;
        }
        
        if (playerScores.size() > limit) {
            hasNextPage = true;
            playerScores.remove((int) limit); 
        }

        response.setScores(playerScores.toArray(new PlayerScore[0]));
        response.setStatus("success");
        response.setHasNextPage(hasNextPage);

        return shiinaAPIHandler.renderJSON(response, shiina, res);
    }

    @Data
    public class PlayerScoresResponse {
        private String status;
        private boolean hasNextPage;
        private PlayerScore[] scores;
    }

    @Data
    public class PlayerScore {
        private int score_id;
        private int user_id;
        private String map_md5;
        private int map_id;
        private int map_set_id;
        private String map_name;
        private double weight;
        private double weight_pp;
        private int max_score;
        private float pp;
        private float acc;
        private String[] mods;
        private String grade;
        private String play_time;
        private int pinned;
    }
}
