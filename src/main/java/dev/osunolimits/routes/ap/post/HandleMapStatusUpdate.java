package dev.osunolimits.routes.ap.post;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    /** the statuses offered in the panel: 0 Pending/Unranked, 2 Ranked, 3 Approved, 4 Qualified, 5 Loved */
    private static final Set<Integer> ALLOWED_STATUSES = Set.of(0, 2, 3, 4, 5);
    private static final int MAX_MAPS = 100;

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

        // AUD-27: only a known status, a numeric set and maps that belong to that set
        String rankingStatus = req.queryParams("rankingStatus");
        String setIdParam = req.queryParams("setId");
        if (rankingStatus == null || !Validation.isNumeric(rankingStatus) || rankingStatus.length() > 2
                || setIdParam == null || !Validation.isNumeric(setIdParam) || setIdParam.length() > 10) {
            return redirect(res, shiina, "/ap/mapranking");
        }
        int status = Integer.parseInt(rankingStatus);
        long setIdLong = Long.parseLong(setIdParam);
        if (!ALLOWED_STATUSES.contains(status) || setIdLong > Integer.MAX_VALUE) {
            return redirect(res, shiina, "/ap/mapranking");
        }
        int setId = (int) setIdLong;

        String[] mapIds = req.queryParamsValues("beatmapIds");
        if (mapIds == null || mapIds.length == 0 || mapIds.length > MAX_MAPS) {
            return redirect(res, shiina, "/ap/mapranking?setId=" + setId);
        }

        List<Integer> ids = new ArrayList<>();
        for (String raw : mapIds) {
            if (raw == null || !Validation.isNumeric(raw) || raw.length() > 10) {
                return redirect(res, shiina, "/ap/mapranking?setId=" + setId);
            }
            long id = Long.parseLong(raw);
            if (id > Integer.MAX_VALUE) {
                return redirect(res, shiina, "/ap/mapranking?setId=" + setId);
            }
            ResultSet rs = shiina.mysql.Query("SELECT 1 FROM `maps` WHERE `id` = ? AND `set_id` = ?", (int) id, setId);
            if (rs == null || !rs.next()) {
                // a map from another set (or none): the audit entry would name the wrong set
                return redirect(res, shiina, "/ap/mapranking?setId=" + setId);
            }
            if (!ids.contains((int) id)) {
                ids.add((int) id);
            }
        }

        for (int id : ids) {
            Beatmap beatmap = new Beatmap();
            beatmap.setId(id);
            beatmap.setStatus(status);
            SyncedAction.changeBeatmapRankStatus(beatmap, status, true, shiina.user.id, shiina.user.name);
        }

        AuditLogger auditLogger = new AuditLogger(shiina.mysql, MessageType.RANK);

        auditLogger.rankMap(shiina.user, setId, status, new Gson().toJson(ids));
        return redirect(res, shiina, "/ap/mapranking?setId=" + setId);
    }
}
