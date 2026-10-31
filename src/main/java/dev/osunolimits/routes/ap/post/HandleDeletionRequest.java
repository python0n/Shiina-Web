package dev.osunolimits.routes.ap.post;

import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.AccountDeletion;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

/** Approve (delete the account) or reject an account deletion request. */
public class HandleDeletionRequest extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        String userIdParam = req.queryParams("userId");
        String action = req.queryParams("action");
        if (userIdParam == null || !Validation.isNumeric(userIdParam) || action == null) {
            return redirect(res, shiina, "/ap/deletions?info=Invalid request");
        }
        int userId = Integer.parseInt(userIdParam);

        boolean exists;
        try (ResultSet rs = shiina.mysql.Query("SELECT 1 FROM `sh_deletion_requests` WHERE `user_id` = ?", userId)) {
            exists = rs != null && rs.next();
        }
        if (!exists) {
            return redirect(res, shiina, "/ap/deletions?info=No such request");
        }

        if ("approve".equals(action)) {
            shiina.mysql.Exec("DELETE FROM `sh_deletion_requests` WHERE `user_id` = ?", userId);
            // own connection: AccountDeletion closes the one it gets when done
            new AccountDeletion().deleteAccount(userId);
            return redirect(res, shiina, "/ap/deletions?info=Account " + userId + " deleted");
        }
        if ("reject".equals(action)) {
            shiina.mysql.Exec("DELETE FROM `sh_deletion_requests` WHERE `user_id` = ?", userId);
            return redirect(res, shiina, "/ap/deletions?info=Request of " + userId + " rejected");
        }
        return redirect(res, shiina, "/ap/deletions?info=Invalid action");
    }
}
