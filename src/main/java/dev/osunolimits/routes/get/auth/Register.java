package dev.osunolimits.routes.get.auth;

import dev.osunolimits.utils.KnownAccount;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.routes.get.modular.ModuleRegister;
import spark.Request;
import spark.Response;

public class Register extends Shiina {


    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String path = req.queryParams("path");
        if (path != null && !path.isEmpty()) {
            shiina.data.put("refPath", path);
        }

        shiina.data.put("seo", new SEOBuilder("Register", App.customization.get("homeDescription").toString()));

        // one account per player (a second one only with the admin's consent):
        // warn first if this browser or this network already has an account
        if (req.queryParams("confirm") == null) {
            Integer knownId = shiina.loggedIn ? shiina.user.id : KnownAccount.fromCookie(req, shiina.mysql);
            if (knownId != null) {
                shiina.data.put("knownName", KnownAccount.nameOf(shiina.mysql, knownId));
                shiina.data.put("warnReason", "browser");
                return renderTemplate("register_warning.html", shiina, res, req);
            }
            if (KnownAccount.fromIp(req, shiina.mysql) != null) {
                shiina.data.put("warnReason", "network");
                return renderTemplate("register_warning.html", shiina, res, req);
            }
        }

        shiina.data.put("modules", ModuleRegister.getModulesRawForPage("register", req, res, shiina));
        return renderTemplate("register.html", shiina, res, req);
    }
    
}
