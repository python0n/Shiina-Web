package dev.osunolimits.routes.post;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class HandlePinOrder extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        res.type("application/json");

        if (!shiina.loggedIn) {
            res.status(401);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"not logged in\"}";
        }

        String order = req.queryParams("order");
        if (order == null || order.isEmpty() || order.split(",").length > 100) {
            res.status(400);
            shiina.mysql.close();
            return "{\"status\":\"error\",\"message\":\"invalid parameters\"}";
        }

        String[] parts = order.split(",");
        for (String part : parts) {
            if (!Validation.isNumeric(part.trim())) {
                res.status(400);
                shiina.mysql.close();
                return "{\"status\":\"error\",\"message\":\"invalid id in order\"}";
            }
        }

        int pos = 1;
        for (String part : parts) {
            shiina.mysql.Exec(
                "UPDATE scores SET pin_order = ? WHERE id = ? AND userid = ? AND pinned = 1",
                pos, Long.parseLong(part.trim()), shiina.user.id);
            pos++;
        }

        shiina.mysql.close();
        return "{\"status\":\"success\"}";
    }
}
