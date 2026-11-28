package dev.osunolimits.routes.ap.post;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

/** Marks an anticheat report as reviewed (with an optional note) or opens it again. */
public class HandleAnticheatReview extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        if (!shiina.loggedIn
                || !PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
            res.status(403);
            return redirect(res, shiina, "/");
        }

        String back = req.queryParamOrDefault("back", "");
        // only our own list, so the redirect cannot leave the panel
        if (!back.matches("state=(open|reviewed|all)(&source=(lastfm|score|token))?(&user=\\d{1,9})?(&page=\\d{1,6})?")) {
            back = "state=open";
        }
        String idStr = req.queryParamOrDefault("id", "");
        String action = req.queryParamOrDefault("action", "");
        if (!idStr.matches("\\d{1,19}")) {
            return redirect(res, shiina, "/ap/anticheat?" + back);
        }
        long id = Long.parseLong(idStr);

        if ("review".equals(action)) {
            String note = req.queryParamOrDefault("note", "").trim();
            if (note.length() > 255) note = note.substring(0, 255);
            shiina.mysql.Exec(
                    "UPDATE ac_reports SET reviewed_at = NOW(), reviewed_by = ?, note = ? WHERE id = ? AND reviewed_at IS NULL",
                    shiina.user.id, note, id);
        } else if ("reopen".equals(action)) {
            shiina.mysql.Exec(
                    "UPDATE ac_reports SET reviewed_at = NULL, reviewed_by = NULL WHERE id = ?", id);
        }
        return redirect(res, shiina, "/ap/anticheat?" + back + "&done=" + ("reopen".equals(action) ? "reopened" : "reviewed"));
    }
}
