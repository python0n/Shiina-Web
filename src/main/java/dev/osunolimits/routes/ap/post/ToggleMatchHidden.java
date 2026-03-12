package dev.osunolimits.routes.ap.post;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class ToggleMatchHidden extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        try {
            if (!shiina.loggedIn) { res.status(401); return "Unauthorized"; }
            if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER)) {
                res.status(403); return "Forbidden";
            }
            String idParam = req.queryParams("id");
            if (idParam == null) { res.status(400); return "Missing id"; }
            long id = Long.parseLong(idParam);
            shiina.mysql.Exec("UPDATE mp_matches SET hidden = IF(hidden = 1, 0, 1) WHERE id = " + id);
            return "OK";
        } finally {
            if (shiina.mysql != null) shiina.mysql.close();
        }
    }
}
