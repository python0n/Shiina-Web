package dev.osunolimits.routes.post;

import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class HandlePinScore extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        res.type("application/json");

        if (!shiina.loggedIn) {
            res.status(401);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"not logged in\"}";
        }

        String idParam = req.queryParams("id");
        String action = req.queryParams("action");
        if (idParam == null || !Validation.isNumeric(idParam) || action == null
                || (!action.equals("pin") && !action.equals("unpin"))) {
            res.status(400);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"invalid parameters\"}";
        }

        long scoreId = Long.parseLong(idParam);
        ResultSet rs = shiina.mysql.Query("SELECT userid FROM scores WHERE id = ?", scoreId);
        if (!rs.next()) {
            res.status(404);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"score not found\"}";
        }

        if (rs.getInt("userid") != shiina.user.id) {
            res.status(403);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"you can only pin your own scores\"}";
        }

        int pinned = action.equals("pin") ? 1 : 0;
        if (pinned == 1) {
            shiina.mysql.Exec(
                "UPDATE scores SET pinned = 1, pin_order = (SELECT COALESCE(MAX(t.pin_order), 0) + 1 FROM (SELECT pin_order FROM scores WHERE userid = ? AND pinned = 1) t) WHERE id = ?",
                shiina.user.id, scoreId);
        } else {
            shiina.mysql.Exec("UPDATE scores SET pinned = 0, pin_order = 0 WHERE id = ?", scoreId);
        }
        shiina.mysql.close();
        return "{\"status\":\"success\",\"pinned\":" + (pinned == 1) + "}";
    }
}
