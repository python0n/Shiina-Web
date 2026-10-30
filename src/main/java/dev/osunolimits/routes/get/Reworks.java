package dev.osunolimits.routes.get;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

/** PP reworks: the server's scores recalculated with other pp formulas (data from the pp-lab service). */
public class Reworks extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 98);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> t = (java.util.Map<String, String>) shiina.data.get("t");
        String title = t != null && t.get("reworks.title") != null ? t.get("reworks.title") : "PP reworks";
        String description = t != null && t.get("reworks.subtitle") != null ? t.get("reworks.subtitle") : "";
        shiina.data.put("seo", new SEOBuilder(title + " | " + App.customization.get("serverName"), description));
        return renderTemplate("reworks.html", shiina, res, req);
    }
}
