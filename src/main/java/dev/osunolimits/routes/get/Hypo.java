package dev.osunolimits.routes.get;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

public class Hypo extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 99);
        shiina.data.put("seo", new SEOBuilder(
            "PP Era Hypo | " + App.customization.get("serverName"),
            "Sprawdź jak wyglądałoby twoje PP w innej erze systemu PP osu!"
        ));
        return renderTemplate("hypo.html", shiina, res, req);
    }
}
