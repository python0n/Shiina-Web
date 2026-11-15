package dev.osunolimits.routes.get.simple;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

/** "Forgot your password?" page: asks for the account's e-mail address. */
public class ForgotPassword extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (shiina.loggedIn) {
            return redirect(res, shiina, "/");
        }

        shiina.data.put("seo", new SEOBuilder("Forgot password", App.customization.get("homeDescription").toString()));
        return renderTemplate("forgot.html", shiina, res, req);
    }
}
