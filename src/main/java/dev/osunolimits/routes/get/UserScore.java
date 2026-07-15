package dev.osunolimits.routes.get;

import dev.osunolimits.api.ScoreQuery;
import dev.osunolimits.api.ScoreQuery.Score;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class UserScore extends Shiina {


    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        Integer id = null;
        if (req.params("id") != null && Validation.isNumeric(req.params("id"))) {
            id = Integer.parseInt(req.params("id"));
        }

        if(id == null) {
            return notFound(res, shiina);
        }

        ScoreQuery scoreQuery = new ScoreQuery(shiina.mysql);
        Score s = scoreQuery.getScore(id);
        if(s == null) {
            return notFound(res, shiina);
        }

        shiina.data.put("score", s);
        if (shiina.loggedIn && shiina.user.id == s.getUserId()) {
            shiina.data.put("canPin", true);
            java.sql.ResultSet pinRs = shiina.mysql.Query("SELECT pinned FROM scores WHERE id = ?", id);
            if (pinRs.next()) {
                shiina.data.put("scorePinned", pinRs.getInt("pinned") == 1);
            }
        }

        if(s.getBeatmap().getFilename() != null) shiina.data.put("seo", new SEOBuilder(s.getUsername() + " on " + s.getBeatmap().getFilename().replace(".osu", ""),  App.customization.get("homeDescription").toString()));
        
        return renderTemplate("score.html", shiina, res, req);
    }
    
}
