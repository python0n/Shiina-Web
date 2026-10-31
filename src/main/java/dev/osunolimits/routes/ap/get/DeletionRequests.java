package dev.osunolimits.routes.ap.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import lombok.Data;
import spark.Request;
import spark.Response;

/** Account deletion requests waiting for an administrator. */
public class DeletionRequests extends Shiina {

    private static final String SQL = "SELECT d.`user_id`, d.`requested_at`, u.`name`, u.`country`, u.`priv`, "
            + "(SELECT COUNT(*) FROM `scores` s WHERE s.`userid` = d.`user_id`) AS `score_count` "
            + "FROM `sh_deletion_requests` d LEFT JOIN `users` u ON u.`id` = d.`user_id` ORDER BY d.`requested_at` ASC";

    @Data
    public class DeletionRequest {
        private int userId;
        private long requestedAt;
        private String name;
        private String country;
        private int scoreCount;
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 22);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        List<DeletionRequest> requests = new ArrayList<>();
        try (ResultSet rs = shiina.mysql.Query(SQL)) {
            while (rs != null && rs.next()) {
                DeletionRequest r = new DeletionRequest();
                r.setUserId(rs.getInt("user_id"));
                r.setRequestedAt(rs.getLong("requested_at"));
                r.setName(rs.getString("name") == null ? "(deleted)" : rs.getString("name"));
                r.setCountry(rs.getString("country") == null ? "xx" : rs.getString("country"));
                r.setScoreCount(rs.getInt("score_count"));
                requests.add(r);
            }
        }
        shiina.data.put("requests", requests);
        if (req.queryParams("info") != null) {
            shiina.data.put("info", req.queryParams("info"));
        }
        return renderTemplate("ap/deletions.html", shiina, res, req);
    }
}
