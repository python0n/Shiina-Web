package dev.osunolimits.modules.cron;

import java.sql.ResultSet;

import dev.osunolimits.common.Database;
import dev.osunolimits.common.MySQL;
import dev.osunolimits.modules.cron.engine.RunnableCronTask;
import dev.osunolimits.plugins.events.admin.OnMultiAccountDetectionEvent;

public class MultiDetectionTask extends RunnableCronTask {
    private final String MULTI_SQL = "WITH UserPairs AS (SELECT c1.userid AS user1, c2.userid AS user2, c1.uninstall_id, c1.disk_serial, c1.adapters, c1.latest_time FROM client_hashes c1 JOIN client_hashes c2 ON c1.uninstall_id = c2.uninstall_id AND c1.userid != c2.userid) SELECT up.user1, up.user2, u1.name AS user1_name, u2.name AS user2_name, CASE WHEN MAX(uninstall_id) = MIN(uninstall_id) THEN 1 ELSE 0 END AS same_uninstall_id, CASE WHEN MAX(disk_serial) = MIN(disk_serial) THEN 1 ELSE 0 END AS same_disk_serial, CASE WHEN MAX(adapters) = MIN(adapters) THEN 1 ELSE 0 END AS same_adapters, MAX(latest_time) AS latest_time FROM UserPairs up JOIN users u1 ON up.user1 = u1.id JOIN users u2 ON up.user2 = u2.id GROUP BY up.user1, up.user2, u1.name, u2.name HAVING NOT (same_uninstall_id = 0 AND same_disk_serial = 0 AND same_adapters = 0) ORDER BY latest_time DESC;";

    @Override
    public void run() {
        

        try (MySQL mysql = Database.getConnection()){
            ResultSet multiAccountResultSet = mysql.Query(MULTI_SQL);
            while (multiAccountResultSet.next()) {
                int user1 = multiAccountResultSet.getInt("user1");
                int user2 = multiAccountResultSet.getInt("user2");
                boolean sameUninstallId = multiAccountResultSet.getBoolean("same_uninstall_id");
                boolean sameDiskSerial = multiAccountResultSet.getBoolean("same_disk_serial");
                boolean sameAdapters = multiAccountResultSet.getBoolean("same_adapters");

                int level = 0;
                if (sameDiskSerial)
                    level++;
                if (sameUninstallId)
                    level++;
                if (sameAdapters)
                    level++;

                // Check if detection already exists for this user pair
                ResultSet existingDetection = mysql.Query(
                        "SELECT COUNT(*) as count FROM `sh_detections` WHERE `user` = ? AND `target` = ?", user1,
                        user2);
                boolean recordExists = false;
                if (existingDetection.next()) {
                    recordExists = existingDetection.getInt("count") > 0;
                }

                if (!recordExists) {
                    int affectedRows = mysql.Exec(
                            "INSERT INTO `sh_detections`(`user`, `target`, `detection`, `score`) VALUES (?,?,CURRENT_TIMESTAMP(),?)",
                            user1, user2, level);
                    if (affectedRows != -1) {
                        new OnMultiAccountDetectionEvent(user1, user2, level).callListeners();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred while running the hourly task", e);
        }
    }

    @Override
    public String getName() {
        return "MultiDetectionTask";
    }
}