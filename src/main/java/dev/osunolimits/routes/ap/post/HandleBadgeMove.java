package dev.osunolimits.routes.ap.post;

import java.sql.ResultSet;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleBadgeMove extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        try {
            if (!shiina.loggedIn) { res.redirect("/login"); return ""; }
            if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) { res.redirect("/"); return ""; }
            String badgeId = req.queryParams("badge_id");
            String dir = req.queryParams("dir");
            String userId = req.queryParams("userid");
            if (badgeId == null || !Validation.isNumeric(badgeId) || dir == null) { res.redirect("/ap/user?id=" + (userId == null ? "" : userId)); return ""; }
            ResultSet cur = shiina.mysql.Query("SELECT userid, sort_order FROM user_badges WHERE id = ?", badgeId);
            if (cur.next()) {
                int uid = cur.getInt("userid");
                int order = cur.getInt("sort_order");
                userId = String.valueOf(uid);
                String neighborSql = dir.equals("up")
                    ? "SELECT id, sort_order FROM user_badges WHERE userid = ? AND sort_order < ? ORDER BY sort_order DESC LIMIT 1"
                    : "SELECT id, sort_order FROM user_badges WHERE userid = ? AND sort_order > ? ORDER BY sort_order ASC LIMIT 1";
                ResultSet nb = shiina.mysql.Query(neighborSql, uid, order);
                if (nb.next()) {
                    int nbId = nb.getInt("id");
                    int nbOrder = nb.getInt("sort_order");
                    shiina.mysql.Exec("UPDATE user_badges SET sort_order = ? WHERE id = ?", nbOrder, Integer.parseInt(badgeId));
                    shiina.mysql.Exec("UPDATE user_badges SET sort_order = ? WHERE id = ?", order, nbId);
                }
            }
            res.redirect("/ap/user?id=" + userId);
            return "";
        } finally {
            if (shiina.mysql != null) shiina.mysql.close();
        }
    }
}
