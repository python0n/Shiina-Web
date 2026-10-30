package dev.osunolimits.routes.get;

import dev.osunolimits.modules.Shiina;
import spark.Request;
import spark.Response;

/** Old PP Era Hypo page: replaced by /reworks. */
public class Hypo extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        res.redirect("/reworks", 301);
        return "";
    }
}
