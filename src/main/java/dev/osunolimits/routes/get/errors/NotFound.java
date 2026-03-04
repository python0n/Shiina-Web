package dev.osunolimits.routes.get.errors;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

public class NotFound extends Shiina {


    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);
        res.status(404);
        shiina.data.put("seo", new SEOBuilder("Not Found", App.customization.get("homeDescription").toString()));
        return renderTemplate("errors/notfound.html", shiina, res, req);
    }
    
}
