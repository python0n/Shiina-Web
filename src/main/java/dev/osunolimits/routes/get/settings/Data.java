package dev.osunolimits.routes.get.settings;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.NavbarRegister;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class Data extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 102);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        if (req.queryParams("info") != null) {
            shiina.data.put("info", req.queryParams("info"));
        }

        if (req.queryParams("error") != null) {
            shiina.data.put("error", req.queryParams("error"));
        }

        if (req.queryParams("timestamp") != null && Validation.isNumeric(req.queryParams("timestamp"))) {
            shiina.data.put("timestamp", Long.parseLong(req.queryParams("timestamp")));
        }

        try (java.sql.ResultSet rs = shiina.mysql.Query(
                "SELECT `requested_at` FROM `sh_deletion_requests` WHERE `user_id` = ?", shiina.user.id)) {
            if (rs != null && rs.next()) {
                shiina.data.put("deletionRequestedAt", rs.getLong("requested_at"));
            }
        }

        shiina.data.put("pluginNav", NavbarRegister.getSettingsItems());
        shiina.data.put("seo", new SEOBuilder("Settings | Data", App.customization.get("homeDescription").toString()));
        return renderTemplate("settings/data.html", shiina, res, req);
    }

}
