package dev.osunolimits.routes.ap.post;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class DenyMapRequest extends Shiina {
    
    @Override
    public Object handle(Request req, Response res) throws Exception {
         ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if(!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        if(!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.NOMINATOR)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        int reqId;
        int page;
        try {
            reqId = Integer.parseInt(req.queryParams("reqId"));
            page = Integer.parseInt(req.queryParams("page"));
        } catch (NumberFormatException e) {
            res.status(400);
            return "Invalid request parameters";
        }

        shiina.mysql.Exec("UPDATE `map_requests` SET `active` = 0 WHERE `id` = ?", reqId);
        App.log.info("Map request {} denied by admin {} (id={})", reqId, shiina.user.name, shiina.user.id);
        res.redirect("/ap/maprequests?page=" + page);

        return null;
    }

}
