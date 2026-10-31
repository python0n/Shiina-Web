package dev.osunolimits.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;
import dev.osunolimits.utils.osu.OsuConverter;
import lombok.Data;

public class ClanQuery {

    private MySQL mysql;

    public ClanQuery(MySQL mysql) {
        this.mysql = mysql;
    }

    @Data
    public class ClanResponse {
        private int id;
        private String name;
        private String tag;
        private int owner;
        private int members;
        private double competitionValue;

        private List<Integer> userCollage = new ArrayList<>();
    }

    @Data
    public class ClanAcitvityResponse {
        private int id;
        private String name;
        private String mapFilename;
        private int mapId;
        private int set_id;
        private int score_id;
        private String[] mods;
        private String grade;
        private String playTime;
        private double pp;
        private double acc;
    }

    @Data
    public class ClanMemberResponse {
        private int id;
        private String name;
        private String country;
        private int priv;
        private int latestActivity;
    }

    @Data
    public class SingleClanResponse {
        private int id;
        private String name;
        private String created;
        private String tag;
        private String ownerName;
        private String ownerCountry;
        private int ownerLastActivity;
        private int owner;
        private int members;
        private double totalPP;
        private double avgPP;
        private double rankedScore;
        private double acc;
        private int totalPPRank;
        private int avgPPRank;
        private int rankedScoreRank;
        private int accRank;
    }

    public enum CompetitionType {
        TOTALPP,
        AVGPP,
        RANKEDSCORE,
        ACC;

        public static CompetitionType fromName(String name) {
            for (CompetitionType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final String GETCLAN_TOTALPP = "SELECT c.*, (SELECT COUNT(*) FROM users u WHERE u.clan_id = c.id) AS memberCount, (SELECT COALESCE(SUM(s.pp), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS totalPP FROM clans c HAVING totalPP > 0 ORDER BY `totalPP` DESC LIMIT ? OFFSET ?;";
    private final String GETCLAN_AVGPP = "SELECT c.*, (SELECT COUNT(*) FROM users u WHERE u.clan_id = c.id) AS memberCount, (SELECT COALESCE(AVG(s.pp), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS avgPP FROM clans c HAVING avgPP > 0 ORDER BY `avgPP` DESC LIMIT ? OFFSET ?;";
    private final String GETCLAN_RANKEDSCORE = "SELECT c.*, (SELECT COUNT(*) FROM users u WHERE u.clan_id = c.id) AS memberCount, (SELECT COALESCE(SUM(s.rscore), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS rankedScore FROM clans c HAVING rankedScore > 0 ORDER BY `rankedScore` DESC LIMIT ? OFFSET ?;";
    private final String GETCLAN_ACC = "SELECT c.*, (SELECT COUNT(*) FROM users u WHERE u.clan_id = c.id) AS memberCount, (SELECT ROUND(AVG(s.acc), 2) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS acc FROM clans c HAVING acc > 0 ORDER BY `acc` DESC LIMIT ? OFFSET ?;";
    private final String GETCLAN_SINGLE = "WITH ClanRanks AS (SELECT c.id, RANK() OVER (ORDER BY COALESCE(SUM(s.pp), 0) DESC) AS totalPPRank FROM clans c LEFT JOIN users u ON u.clan_id = c.id LEFT JOIN stats s ON u.id = s.id AND s.mode = ? GROUP BY c.id), AvgPPRanks AS (SELECT c.id, RANK() OVER (ORDER BY COALESCE(AVG(s.pp), 0) DESC) AS avgPPRank FROM clans c LEFT JOIN users u ON u.clan_id = c.id LEFT JOIN stats s ON u.id = s.id AND s.mode = ? GROUP BY c.id), RankedScoreRanks AS (SELECT c.id, RANK() OVER (ORDER BY COALESCE(SUM(s.rscore), 0) DESC) AS rankedScoreRank FROM clans c LEFT JOIN users u ON u.clan_id = c.id LEFT JOIN stats s ON u.id = s.id AND s.mode = ? GROUP BY c.id), AccRanks AS (SELECT c.id, RANK() OVER (ORDER BY ROUND(AVG(s.acc), 2) DESC) AS accRank FROM clans c LEFT JOIN users u ON u.clan_id = c.id LEFT JOIN stats s ON u.id = s.id AND s.mode = ? GROUP BY c.id) SELECT u.name AS `owner_name`, u.latest_activity AS `owner_online`, u.country AS `owner_country`,c.*, (SELECT COUNT(*) FROM users u WHERE u.clan_id = c.id) AS memberCount, (SELECT COALESCE(SUM(s.pp), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS totalPP, cr.totalPPRank, (SELECT COALESCE(AVG(s.pp), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS avgPP, apr.avgPPRank, (SELECT COALESCE(SUM(s.rscore), 0) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS rankedScore, rsr.rankedScoreRank, (SELECT ROUND(AVG(s.acc), 2) FROM users u JOIN stats s ON u.id = s.id WHERE u.clan_id = c.id AND s.mode = ?) AS acc, ar.accRank FROM clans c LEFT JOIN users u ON owner = u.id LEFT JOIN ClanRanks cr ON cr.id = c.id LEFT JOIN AvgPPRanks apr ON apr.id = c.id LEFT JOIN RankedScoreRanks rsr ON rsr.id = c.id LEFT JOIN AccRanks ar ON ar.id = c.id WHERE c.id = ?;";
    private final String GETCLAN_MEMBERS = "SELECT `id`, `name`, `country`, `priv`, `latest_activity` FROM `users` WHERE `clan_id` = ? AND clan_priv != 3 AND `priv` & 1;";
    private final String GETCLAN_ACTIVITY = "SELECT s.`userid` AS `user_id`, u.`name`, m.`filename`, m.`id` AS `map_id`, m.`set_id`, s.`play_time`, s.`mods`, s.`pp`, s.`id` AS `score_id`, s.`acc`, s.`grade` FROM `scores` s INNER JOIN `users` u ON s.`userid` = u.`id` INNER JOIN `maps` m ON m.`md5` = s.`map_md5` INNER JOIN ( SELECT `userid`, `map_md5`, `mods`, MAX(`play_time`) AS `max_play_time` FROM `scores` WHERE `status` = 2 AND `mode` = ? GROUP BY `userid`, `map_md5`, `mods` ) latest ON s.`userid` = latest.`userid` AND s.`map_md5` = latest.`map_md5` AND s.`mods` = latest.`mods` AND s.`play_time` = latest.`max_play_time` WHERE u.`clan_id` = ? AND u.`priv` & 1 AND s.`status` = 2 AND s.`mode` = ? ORDER BY s.`play_time` DESC LIMIT 20;";

    public List<ClanAcitvityResponse> getClanActivity(int id, int mode) throws SQLException {
        List<ClanAcitvityResponse> responses = new ArrayList<>();
        ResultSet rs = mysql.Query(GETCLAN_ACTIVITY, mode, id, mode);
        while (rs.next()) {
            ClanAcitvityResponse response = new ClanAcitvityResponse();
            response.setId(rs.getInt("user_id"));
            response.setName(rs.getString("name"));
            response.setMapFilename(rs.getString("filename"));
            response.setMapId(rs.getInt("map_id"));
            response.setSet_id(rs.getInt("set_id"));
            response.setScore_id(rs.getInt("score_id"));
            response.setMods(OsuConverter.convertMods(rs.getInt("mods")));
            response.setGrade(rs.getString("grade"));
            response.setPlayTime(rs.getString("play_time"));
            response.setAcc(rs.getDouble("acc"));
            response.setPp(rs.getDouble("pp"));
            responses.add(response);
        }
        return responses;
    }

    public List<ClanMemberResponse> getMembers(int id) throws SQLException {
        List<ClanMemberResponse> responses = new ArrayList<>();
        ResultSet clanMembers = mysql.Query(GETCLAN_MEMBERS, id);

        while (clanMembers.next()) {
            ClanMemberResponse response = new ClanMemberResponse();
            response.setId(clanMembers.getInt("id"));
            response.setCountry(clanMembers.getString("country"));
            response.setName(clanMembers.getString("name"));
            response.setPriv(clanMembers.getInt("priv"));
            response.setLatestActivity(clanMembers.getInt("latest_activity"));
            responses.add(response);
        }
        return responses;
    }

    public SingleClanResponse getClan(int mode, int id) throws SQLException {
        if (mode > 8 || mode == 7 || mode < 0)
            return null;

        ResultSet rs = mysql.Query(GETCLAN_SINGLE, String.valueOf(mode), String.valueOf(mode), String.valueOf(mode),
                String.valueOf(mode), String.valueOf(mode), String.valueOf(mode), String.valueOf(mode),
                String.valueOf(mode), String.valueOf(id));
        if (!rs.next())
            return null;

        SingleClanResponse response = new SingleClanResponse();
        response.setId(rs.getInt("id"));
        response.setName(rs.getString("name"));
        response.setTag(rs.getString("tag"));
        response.setCreated(rs.getString("created_at"));
        response.setOwnerName(rs.getString("owner_name"));
        response.setOwnerLastActivity(rs.getInt("owner_online"));
        response.setOwnerCountry(rs.getString("owner_country"));
        response.setOwner(rs.getInt("owner"));
        response.setMembers(rs.getInt("memberCount"));
        response.setTotalPP(rs.getDouble("totalPP"));
        response.setAvgPP(rs.getDouble("avgPP"));
        response.setRankedScore(rs.getDouble("rankedScore"));
        response.setAcc(rs.getDouble("acc"));
        response.setTotalPPRank(rs.getInt("totalPPRank"));
        response.setAvgPPRank(rs.getInt("avgPPRank"));
        response.setRankedScoreRank(rs.getInt("rankedScoreRank"));
        response.setAccRank(rs.getInt("accRank"));

        return response;
    }

    public List<ClanResponse> getClan(CompetitionType type, int mode, int limit, int offset) {
        List<ClanResponse> responses = new ArrayList<>();
        ResultSet rs = null;
        String competitionValueName = "";

        switch (type) {
            case TOTALPP:
                rs = mysql.Query(GETCLAN_TOTALPP, String.valueOf(mode), String.valueOf(limit), String.valueOf(offset));
                competitionValueName = "totalPP";
                break;
            case AVGPP:
                rs = mysql.Query(GETCLAN_AVGPP, String.valueOf(mode), String.valueOf(limit), String.valueOf(offset));
                competitionValueName = "avgPP";
                break;
            case RANKEDSCORE:
                rs = mysql.Query(GETCLAN_RANKEDSCORE, String.valueOf(mode), String.valueOf(limit),
                        String.valueOf(offset));
                competitionValueName = "rankedScore";
                break;
            case ACC:
                rs = mysql.Query(GETCLAN_ACC, String.valueOf(mode), String.valueOf(limit), String.valueOf(offset));
                competitionValueName = "acc";
                break;
        }
        try {
            while (rs.next()) {
                ClanResponse response = new ClanResponse();

                ResultSet userCollageRs = mysql.Query("SELECT `id` FROM users WHERE clan_id = ? ORDER BY (clan_priv = 3) DESC, id LIMIT 4;", rs.getInt("id"));
                while (userCollageRs.next()) {
                    response.getUserCollage().add(userCollageRs.getInt("id"));
                }

                response.setId(rs.getInt("id"));
                response.setName(rs.getString("name"));
                response.setTag(rs.getString("tag"));
                response.setOwner(rs.getInt("owner"));
                response.setMembers(rs.getInt("memberCount"));
                response.setCompetitionValue(rs.getDouble(competitionValueName));
                responses.add(response);
            }
        } catch (Exception e) {
            App.log.error("Failed to get clan data for [" + type.name() + "]", e);

        }
        return responses;

    }
}
