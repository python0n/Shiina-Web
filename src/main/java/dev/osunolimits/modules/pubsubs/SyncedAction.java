package dev.osunolimits.modules.pubsubs;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import dev.osunolimits.common.Database;
import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;
import dev.osunolimits.models.Beatmap;
import dev.osunolimits.modules.pubsubs.PubSubModels.AddPrivInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.AlertAllInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.CountryChangeInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.GiveDonatorInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.NameChangeInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.RankOutput;
import dev.osunolimits.modules.pubsubs.PubSubModels.RemovePrivInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.RestrictInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.UnrestrictInput;
import dev.osunolimits.modules.pubsubs.PubSubModels.WipeInput;
import dev.osunolimits.modules.utils.UserInfoCache;
import dev.osunolimits.utils.Auth;

public class SyncedAction {

    private static final Logger logger = LoggerFactory.getLogger("SyncedAction");
    private static final List<String> profileExtensions = List.of(".png", ".jpg", ".gif");

    private static final Gson gson = new Gson();

    public static void removeProfilePicture(int userId) {
        File avatarDir = new File(App.env.get("AVATARFOLDER"));

        for (String extension : profileExtensions) {
            File avatar = new File(avatarDir, userId + extension);
            if (avatar.exists() && avatar.isFile()) {
                avatar.delete();
                logger.debug("Deleted profile picture of {}", userId);
            }
        }
    }

    public static void removeUserpage(int userId) {
        try (MySQL mysql = Database.getConnection()) {
            ResultSet userpageRs = mysql.Query("SELECT * FROM `userpages` WHERE `user_id` = ?", userId);
            try {
                if (userpageRs.next()) {
                    mysql.Exec("DELETE FROM `userpages` WHERE `user_id` = ?", userId);
                }
            } catch (SQLException e) {
                logger.error("Failed to remove userpage of {}", userId, e);
            }
        }
    }

    // Privileges: normal, verified, whitelisted, etc.
    public static void removePriv(int userId, String[] privs) {
        RemovePrivInput removePrivInput = new RemovePrivInput();
        removePrivInput.setId(userId);
        removePrivInput.setPrivs(privs);

        App.jedisPool.publish("removepriv", gson.toJson(removePrivInput));
        sleepQuietly(500);

        UserInfoCache.reloadUser(userId);
    }

    // Privileges: normal, verified, whitelisted, etc.
    public static void addPriv(int userId, String[] privs) {
        AddPrivInput addPrivInput = new AddPrivInput();
        addPrivInput.setId(userId);
        addPrivInput.setPrivs(privs);

        App.jedisPool.publish("addpriv", gson.toJson(addPrivInput));
        sleepQuietly(500);

        UserInfoCache.reloadUser(userId);
    }

    // Durations: s/h/m/d/w
    public static void addDonatorStatus(int userId, String duration) {
        GiveDonatorInput giveDonatorInput = new GiveDonatorInput();
        giveDonatorInput.setId(userId);
        giveDonatorInput.setDuration(duration);

        App.jedisPool.publish("givedonator", gson.toJson(giveDonatorInput));
        sleepQuietly(500);

        UserInfoCache.reloadUser(userId);
    }

    public static void alertAll(String message) {
        AlertAllInput alertAllInput = new AlertAllInput();
        alertAllInput.setMessage(message);

        App.jedisPool.publish("alert_all", gson.toJson(alertAllInput));
        sleepQuietly(500);
    }

    public static void wipe(int userId, int mode) {
        WipeInput wipeInput = new WipeInput();
        wipeInput.setId(userId);
        wipeInput.setMode(mode);

        App.jedisPool.publish("wipe", gson.toJson(wipeInput));
        sleepQuietly(500);
    }

    public static void restrict(int userId, int adminId, String reason) {
        RestrictInput restrictInput = new RestrictInput();
        restrictInput.setId(userId);
        restrictInput.setUserId(adminId);
        restrictInput.setReason(reason);

        App.jedisPool.publish("restrict", gson.toJson(restrictInput));
        sleepQuietly(500);
    }

    public static void unrestrict(int userId, int adminId, String reason) {
        UnrestrictInput unrestrictInput = new UnrestrictInput();
        unrestrictInput.setId(userId);
        unrestrictInput.setUserId(adminId);
        unrestrictInput.setReason(reason);

        App.jedisPool.publish("unrestrict", gson.toJson(unrestrictInput));
        sleepQuietly(500);
    }

    public static void changeBeatmapRankStatus(Beatmap beatmap, int newStatus, boolean newFrozenStatus, int userId, String userName) {
        int beatmapId;
        try (MySQL mysql = Database.getConnection()) {
            beatmapId = beatmap.getId();
            beatmap.setStatus(newStatus);
            beatmap.setFrozen(newFrozenStatus);

            int affectedRows = mysql.Exec("UPDATE beatmaps SET status = ?, frozen = ? WHERE id = ?", newStatus,
                    newFrozenStatus ? 1 : 0, beatmapId);
            if (affectedRows == 0) {
                App.log.error("Failed to update beatmap status for beatmap id: " + beatmapId);
            }
        }
        RankOutput rankOutput = new RankOutput();
        rankOutput.setBeatmap_id(beatmapId);
        rankOutput.setStatus(newStatus);
        rankOutput.setFrozen(newFrozenStatus);
        rankOutput.setUser_id(userId);
        rankOutput.setUser_name(userName);
        rankOutput.setUser_id(userId);
        rankOutput.setUser_name(userName);

        App.jedisPool.publish("rank", gson.toJson(rankOutput));
        sleepQuietly(500);
    }

    public static void changeCountryFlag(int userId, String flag) {
        CountryChangeInput input = new CountryChangeInput();
        input.id = userId;
        input.country = flag.toLowerCase();

        App.jedisPool.publish("country_change", gson.toJson(input));
        sleepQuietly(500);
    }

    public static void changeName(int userId, String newName) {
        String newSafeName = newName.toLowerCase().replaceAll(" ", "_");
        NameChangeInput input = new NameChangeInput();
        input.id = userId;
        input.name = newName;

        try (MySQL mysql = Database.getConnection()) {
            mysql.Exec("UPDATE `users` SET `name`=?,`safe_name`=? WHERE `id` = ?", newName, newSafeName, userId);
        }

        App.jedisPool.publish("name_change", gson.toJson(input));
        sleepQuietly(500);

        UserInfoCache.reloadUser(userId);
    }

    public static String generateRecoveryToken(int userId) {
        String token = Auth.generateNewToken();

        try (MySQL mysql = Database.getConnection()) {
            mysql.Exec("INSERT INTO `sh_recovery`(`token`, `user`) VALUES (?,?)", token, userId);
        }

        return token;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Sleep interrupted while waiting for pub/sub propagation", e);
        }
    }

}
