package dev.osunolimits.routes.post.settings.data;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

/**
 * Players don't delete their account themselves any more: they send a request,
 * which an administrator approves or rejects in the admin panel (/ap/deletions).
 */
public class HandleAccountDeletion extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/settings/data");
        }

        if ("cancel".equals(req.queryParams("action"))) {
            shiina.mysql.Exec("DELETE FROM `sh_deletion_requests` WHERE `user_id` = ?", shiina.user.id);
            return redirect(res, shiina, "/settings/data?info=Account deletion request cancelled");
        }

        shiina.mysql.Exec(
                "INSERT IGNORE INTO `sh_deletion_requests` (`user_id`, `requested_at`) VALUES (?, ?)",
                shiina.user.id, System.currentTimeMillis() / 1000);
        return redirect(res, shiina, "/settings/data?info=Account deletion requested, an administrator will review it");
    }
}
