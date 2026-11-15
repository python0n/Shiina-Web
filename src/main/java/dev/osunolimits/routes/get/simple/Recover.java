package dev.osunolimits.routes.get.simple;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.PasswordReset;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

public class Recover extends Shiina {

    public static final String EXPIRED_MESSAGE = "This link has expired or was already used. Ask for a new one.";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String token = req.queryParams("token");
        if (token == null) {
            return redirect(res, shiina, "/");
        }

        int userId = PasswordReset.findUser(shiina.mysql, token);
        if (userId > 0) {
            shiina.data.put("userid", String.valueOf(userId));
            shiina.data.put("token", token);
        } else {
            shiina.data.put("error", EXPIRED_MESSAGE);
        }

        shiina.data.put("seo", new SEOBuilder("Recover", App.customization.get("homeDescription").toString()));
        return renderTemplate("recover.html", shiina, res, req);
    }

}
