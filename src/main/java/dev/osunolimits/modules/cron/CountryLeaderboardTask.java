package dev.osunolimits.modules.cron;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import dev.osunolimits.common.Database;
import dev.osunolimits.common.MySQL;
import dev.osunolimits.modules.cron.engine.RunnableCronTask;
import lombok.Data;

public class CountryLeaderboardTask extends RunnableCronTask {

    @Data
    public class CountryLeaderboardResponse {
        private List<String> countries;
        private long created;
    }

    private static final ConcurrentHashMap<Integer, CountryLeaderboardResponse> cache = new ConcurrentHashMap<>();

    @Override
    public void run() {
        for (int mode = 0; mode <= 8; mode++) {

            if (mode == 7)
                continue;

            try (MySQL mysql = Database.getConnection()) {
                CountryLeaderboardResponse cacheObject = new CountryLeaderboardResponse();
                ResultSet countriesResultSet = mysql.Query(
                        "SELECT COUNT(`id`) AS `people`, `country` FROM `users` WHERE `id` IN ( SELECT `userid` FROM `scores` WHERE `mode` = ? AND `scores`.`status`!= 0 ) GROUP BY `country` ORDER BY `people` DESC;",
                        String.valueOf(mode));

                List<String> countries = new ArrayList<>();
                while (countriesResultSet.next()) {
                    countries.add(countriesResultSet.getString("country"));
                }

                cacheObject.setCountries(countries);
                cacheObject.setCreated(System.currentTimeMillis() / 1000);

                cache.put(mode, cacheObject);

            } catch (SQLException e) {
                logger.error("Error fetching country leaderboard for mode " + mode, e);
            }
        }
    }

    @Override
    public String getName() {
        return "CountryLeaderboardTask";
    }

    public static ArrayList<String> get(int mode) {
        if (cache.containsKey(mode)) {
            return new ArrayList<>(cache.get(mode).getCountries());
        }
        return new ArrayList<>();
    }

}
