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
    public static String removePriv(int userId, String[] privs) {
        return removePriv(userId, privs, 0);
    }

    public static String removePriv(int userId, String[] privs, int adminId) {
        RemovePrivInput removePrivInput = new RemovePrivInput();
        removePrivInput.setId(userId);
        removePrivInput.setUserId(adminId);
        removePrivInput.setPrivs(privs);

        String result = publishAndWait("removepriv", removePrivInput);

        UserInfoCache.reloadUser(userId);
        return result;
    }

    // Privileges: normal, verified, whitelisted, etc.
    public static String addPriv(int userId, String[] privs) {
        return addPriv(userId, privs, 0);
    }

    public static String addPriv(int userId, String[] privs, int adminId) {
        AddPrivInput addPrivInput = new AddPrivInput();
        addPrivInput.setId(userId);
        addPrivInput.setUserId(adminId);
        addPrivInput.setPrivs(privs);

        String result = publishAndWait("addpriv", addPrivInput);

        UserInfoCache.reloadUser(userId);
        return result;
    }

    // Durations: s/h/m/d/w
    public static String addDonatorStatus(int userId, String duration) {
        GiveDonatorInput giveDonatorInput = new GiveDonatorInput();
        giveDonatorInput.setId(userId);
        giveDonatorInput.setDuration(duration);

        String result = publishAndWait("givedonator", giveDonatorInput);

        UserInfoCache.reloadUser(userId);
        return result;
    }

    public static String alertAll(String message) {
        AlertAllInput alertAllInput = new AlertAllInput();
        alertAllInput.setMessage(message);

        return publishAndWait("alert_all", alertAllInput);
    }

    public static String wipe(int userId, int mode) {
        WipeInput wipeInput = new WipeInput();
        wipeInput.setId(userId);
        wipeInput.setMode(mode);

        return publishAndWait("wipe", wipeInput);
    }

    public static String restrict(int userId, int adminId, String reason) {
        RestrictInput restrictInput = new RestrictInput();
        restrictInput.setId(userId);
        restrictInput.setUserId(adminId);
        restrictInput.setReason(reason);

        return publishAndWait("restrict", restrictInput);
    }

    public static String unrestrict(int userId, int adminId, String reason) {
        UnrestrictInput unrestrictInput = new UnrestrictInput();
        unrestrictInput.setId(userId);
        unrestrictInput.setUserId(adminId);
        unrestrictInput.setReason(reason);

        return publishAndWait("unrestrict", unrestrictInput);
    }

    /**
     * The status is written only by bancho (table `maps` and its own cache), after it checks
     * the map and the status. Shiina used to write to a separate `beatmaps` table as well,
     * which bancho never reads (AUD-28).
     */
    public static String changeBeatmapRankStatus(Beatmap beatmap, int newStatus, boolean newFrozenStatus, int userId, String userName) {
        int beatmapId = beatmap.getId();
        beatmap.setStatus(newStatus);
        beatmap.setFrozen(newFrozenStatus);

        RankOutput rankOutput = new RankOutput();
        rankOutput.setBeatmap_id(beatmapId);
        rankOutput.setStatus(newStatus);
        rankOutput.setFrozen(newFrozenStatus);
        rankOutput.setUser_id(userId);
        rankOutput.setUser_name(userName);

        return publishAndWait("rank", rankOutput);
    }

    public static void changeCountryFlag(int userId, String flag) {
        CountryChangeInput input = new CountryChangeInput();
        input.id = userId;
        input.country = flag.toLowerCase();

        App.jedisPool.publish("country_change", gson.toJson(input));
        sleepQuietly(500);
    }

    /**
     * Tell bancho a clan changed (join, leave, kick, create), so online players
     * get the new clan in game without logging in again.
     */
    public static void clanChanged(int... userIds) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("ids", userIds);
        publishQuietly("clan_change", gson.toJson(payload));
    }

    /** Same for every member of a clan (disband). Call before the members are removed. */
    public static void clanDisbanded(int clanId) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("clan_id", clanId);
        publishQuietly("clan_change", gson.toJson(payload));
    }

    /**
     * Tell bancho the password of an account was changed: it ends every session of
     * that account (game client, IRC, website chat), so nobody stays logged in with
     * the old password.
     */
    public static void passwordChanged(int userId) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", userId);
        publishQuietly("password_changed", gson.toJson(payload));
    }

    /** AUD-12: how long the panel waits for bancho to say what happened */
    private static final int ACK_TIMEOUT_SECONDS = 5;
    public static final String SUCCESS = "success";
    public static final String NO_ANSWER = "no answer from bancho (is it running?)";

    /**
     * Sends a panel action to bancho with a request id and waits up to a few seconds
     * for bancho's answer ("success" or the reason it refused). Before this the panel
     * always showed success, also when bancho refused or wasn't running.
     */
    private static String publishAndWait(String channel, Object input) {
        String reqId = java.util.UUID.randomUUID().toString().replace("-", "");
        com.google.gson.JsonObject payload = gson.toJsonTree(input).getAsJsonObject();
        payload.addProperty("req_id", reqId);
        try {
            App.jedisPool.publish(channel, payload.toString());
            List<String> answer = App.jedisPool.blpop(ACK_TIMEOUT_SECONDS, "ex:ack:" + reqId);
            if (answer == null || answer.size() < 2) {
                logger.warn("No answer from bancho for {}", channel);
                return NO_ANSWER;
            }
            return answer.get(1);
        } catch (Exception e) {
            logger.warn("Could not send {} to bancho: {}", channel, e.getMessage());
            return "could not reach Redis";
        }
    }

    private static void publishQuietly(String channel, String json) {
        try {
            App.jedisPool.publish(channel, json);
        } catch (Exception e) {
            LoggerFactory.getLogger(SyncedAction.class).warn("Could not publish " + channel + ": " + e.getMessage());
        }
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

    /** Link made by an admin in the panel: the same one-time tokens as e-mail resets, valid for a day. */
    public static String generateRecoveryToken(int userId) {
        try (MySQL mysql = Database.getConnection()) {
            return dev.osunolimits.modules.utils.PasswordReset.create(mysql, userId,
                    dev.osunolimits.modules.utils.PasswordReset.ADMIN_TTL_SECONDS);
        }
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
