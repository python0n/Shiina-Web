package dev.osunolimits.routes.ap.post;

import com.google.gson.Gson;

import dev.osunolimits.models.Beatmap;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.pubsubs.SyncedAction;
import dev.osunolimits.modules.utils.AuditLogger;
import dev.osunolimits.routes.ap.api.PubSubHandler.MessageType;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleMapStatusUpdate extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if(!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        if(!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.NOMINATOR)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        String rankingStatus = req.queryParams("rankingStatus");
        if (rankingStatus == null || rankingStatus.isEmpty() &&  Validation.isNumeric(rankingStatus)) {
            return redirect(res, shiina, "/");
        }

        String[] mapIds = req.queryParamsValues("beatmapIds");
        if (mapIds == null || mapIds.length == 0) {
            return redirect(res, shiina, "/");
        }

        
        for (String string : mapIds) {
            Beatmap beatmap = new Beatmap();
            beatmap.setId(Integer.parseInt(string));
            beatmap.setStatus(Integer.parseInt(rankingStatus));
            SyncedAction.changeBeatmapRankStatus(beatmap, beatmap.getStatus(), true, shiina.user.id, shiina.user.name);
        }

        AuditLogger auditLogger = new AuditLogger(shiina.mysql, MessageType.RANK);

        auditLogger.rankMap(shiina.user, Integer.parseInt(req.queryParams("setId")), Integer.parseInt(rankingStatus), new Gson().toJson(mapIds));
        return redirect(res, shiina, "/ap/mapranking?setId="+ req.queryParams("setId"));
    }
}
