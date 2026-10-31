package dev.osunolimits.modules.cron;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import dev.osunolimits.common.Database;
import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.cron.engine.RunnableCronTask;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.resps.Tuple;

public class ShiinaRankCache extends RunnableCronTask {

    @Override
    public void run() {
        JedisPooled jedis = App.jedisPool;
        try (MySQL mysql = Database.getConnection()) {
            for (int mode = 0; mode <= 8; mode++) {
                if (mode == 7)
                    continue;

                List<Tuple> topPlayers = jedis.zrevrangeWithScores("bancho:leaderboard:" + mode, 0, -1);
                int rank = 0;
                for (Tuple player : topPlayers) {
                    rank++;
                    String userId = player.getElement();
                    ResultSet userStatsRs = mysql.Query("SELECT * FROM `stats` WHERE `id` = ? AND `mode` = ?",
                            userId, mode);
                    try {
                        if (!userStatsRs.next()) {
                            App.log.debug("User ID " + userId + " not found in stats for mode " + mode);
                            continue;
                        }

                        long userScore = userStatsRs.getLong("tscore");
                        if (userScore == 0) {
                            continue;
                        }
                    } catch (SQLException e) {
                        App.log.error("Error fetching user stats for user ID " + userId + " in mode " + mode, e);
                    }
                    mysql.Exec(
                            "INSERT INTO `sh_rank_cache`(`user_id`, `date`, `mode`, `rank`) VALUES (?,CURDATE(),?,?)",
                            userId, mode, rank);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "ShiinaRankCache";
    }
}
