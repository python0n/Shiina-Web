package dev.osunolimits.routes.get.simple;

import java.sql.ResultSet;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import spark.Request;
import spark.Response;

public class Recover extends Shiina {


    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String token = req.queryParams("token");
        if(token == null) {
            return redirect(res, shiina, "/");
        }else {
            ResultSet recoverRs = shiina.mysql.Query("SELECT * FROM `sh_recovery` WHERE `token` = ?", token);
            if(recoverRs.next()) {
                shiina.data.put("userid", recoverRs.getString("user"));
                shiina.data.put("token", recoverRs.getString("token"));
            }else {
                return redirect(res, shiina, "/");
            }
        }

        shiina.data.put("seo", new SEOBuilder("Recover", App.customization.get("homeDescription").toString()));
        return renderTemplate("recover.html", shiina, res, req);
    }
    
}
