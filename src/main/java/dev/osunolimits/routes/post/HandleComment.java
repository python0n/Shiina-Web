package dev.osunolimits.routes.post;

import org.owasp.encoder.Encode;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.routes.api.get.ShiinaAPIHandler;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleComment extends Shiina {

    private enum Target {
        USER,
        MAP,
        REPLAY
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        ShiinaAPIHandler shiinaAPIHandler = new ShiinaAPIHandler();

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/");
        }

        if(!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.UNRESTRICTED)) {
            return redirect(res, shiina, "/");
        }

        String message = null;
        if (req.queryParams("message") != null) {
            message = req.queryParams("message");
        }else {
            shiinaAPIHandler.addRequiredParameter("message", "string", "missing");
        }

        String target = null;
        if (req.queryParams("target") != null) {
            target = req.queryParams("target");
        }else {
            shiinaAPIHandler.addRequiredParameter("target", "string", "missing");
        }

        if (shiinaAPIHandler.hasIssues()) {
            return shiinaAPIHandler.renderIssues(shiina, res);
        }

        String targetId = null;
        if (req.queryParams("id") != null && Validation.isNumeric(req.queryParams("id"))) {
            targetId = req.queryParams("id");
        }else {
            shiinaAPIHandler.addRequiredParameter("target_id", "int", "missing or invalid");
        }

        Target targetType = Target.valueOf(target.toUpperCase());
        if (targetType == null) {
            shiinaAPIHandler.addRequiredParameter("target", "string", "invalid");
        }

        if (shiinaAPIHandler.hasIssues()) {
            return shiinaAPIHandler.renderIssues(shiina, res);
        }

        String encodedMessage = Encode.forHtmlContent(message);
        
        if(message.length() > 0) {
            shiina.mysql.Exec("INSERT INTO `comments`(`target_id`, `target_type`, `userid`, `time`, `comment`) VALUES (?,?,?,?,?)", targetId, target.toLowerCase(), shiina.user.id, 0, encodedMessage);
            if (targetType == Target.MAP) {
                notifyMapOwner(shiina, Integer.parseInt(targetId));
            }
        }
        
        String redirectUrl = "";

        switch (targetType) {
            case MAP:
                redirectUrl += "/b/";
                break;
            default:
                return "ERROR";
        }

        redirectUrl += targetId;

        return redirect(res, shiina, redirectUrl);
    }

    /** The owner of a map uploaded through BSS gets a notification about the comment. */
    private void notifyMapOwner(ShiinaRequest shiina, int mapId) {
        try {
            java.sql.ResultSet rs = shiina.mysql.Query(
                    "SELECT m.artist, m.title, m.version, b.owner_id FROM maps m "
                            + "JOIN bss_sets b ON b.set_id = m.set_id WHERE m.id = ? AND m.set_id >= "
                            + dev.osunolimits.routes.api.chat.NotificationsApi.BSS_SET_ID_MIN,
                    mapId);
            if (rs != null && rs.next() && rs.getInt("owner_id") != shiina.user.id) {
                java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("user", shiina.user.name);
                data.put("map", rs.getString("artist") + " - " + rs.getString("title") + " [" + rs.getString("version") + "]");
                dev.osunolimits.routes.api.chat.NotificationsApi.add(shiina.mysql, rs.getInt("owner_id"), "map_comment", data, "/b/" + mapId);
            }
        } catch (Exception e) {
            dev.osunolimits.main.App.log.warn("Comment notification failed: " + e.getMessage());
        }
    }
}
