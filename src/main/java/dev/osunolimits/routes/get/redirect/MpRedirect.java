package dev.osunolimits.routes.get.redirect;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;

public class MpRedirect extends Shiina {

    @Override
    public Object handle(spark.Request req, spark.Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        String idStr = req.params("id");
        if (idStr == null || !idStr.matches("\\d+")) {
            return notFound(res, shiina);
        }

        return redirect(res, shiina, "/matches/" + idStr);
    }
}
