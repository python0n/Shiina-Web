package dev.osunolimits.routes.ap.post;

import java.io.File;
import java.sql.ResultSet;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleBadgeDelete extends Shiina {
    private static final String BADGE_DIR = "data/badges";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        try {
            if (!shiina.loggedIn) { res.redirect("/login"); return ""; }
            if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) { res.redirect("/"); return ""; }
            String badgeId = req.queryParams("badge_id");
            String userId = req.queryParams("userid");
            if (badgeId == null || !Validation.isNumeric(badgeId)) { res.redirect("/ap/user?id=" + (userId == null ? "" : userId)); return ""; }
            ResultSet rs = shiina.mysql.Query("SELECT image, userid FROM user_badges WHERE id = ?", badgeId);
            if (rs.next()) {
                String image = rs.getString("image");
                if (userId == null) userId = String.valueOf(rs.getInt("userid"));
                if (image != null && image.matches("[a-zA-Z0-9_.-]{1,80}")) {
                    File f = new File(BADGE_DIR, image);
                    if (f.exists()) f.delete();
                }
                shiina.mysql.Exec("DELETE FROM user_badges WHERE id = ?", badgeId);
            }
            res.redirect("/ap/user?id=" + userId);
            return "";
        } finally {
            if (shiina.mysql != null) shiina.mysql.close();
        }
    }
}
