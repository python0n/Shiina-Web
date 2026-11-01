package dev.osunolimits.routes.get;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

/** /pp - pp calculator for any map, mods and accuracy (calculated by the pp-lab service). */
public class PpCalc extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 98);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> t = (java.util.Map<String, String>) shiina.data.get("t");
        String title = t != null && t.get("calc.title") != null ? t.get("calc.title") : "pp calculator";
        String description = t != null && t.get("calc.subtitle") != null ? t.get("calc.subtitle") : "";
        shiina.data.put("seo", new SEOBuilder(title, description));
        return renderTemplate("ppcalc.html", shiina, res, req);
    }
}
