package dev.osunolimits.routes.get.user;

import java.sql.ResultSet;
import java.util.ArrayList;

import dev.osunolimits.main.App;
import dev.osunolimits.models.UserInfoObject;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.modules.utils.ShiinaSupporterBadge;
import dev.osunolimits.utils.osu.PermissionHelper;
import dev.osunolimits.utils.Visibility;
import lombok.Data;
import spark.Request;
import spark.Response;

public class Relations extends Shiina {

    private final String RELATION_QUERY = "SELECT DISTINCT CASE WHEN EXISTS ( SELECT 1 FROM relationships r2 WHERE r2.user1 = r.user2 AND r2.user2 = r.user1 ) THEN 'mutual' WHEN r.user1 = ? THEN 'known' ELSE 'follower' END AS status, CASE WHEN r.user1 = ? THEN r.user2 ELSE r.user1 END AS id, CASE WHEN r.user1 = ? THEN u2.name ELSE u1.name END AS name, CASE WHEN r.user1 = ? THEN u2.latest_activity ELSE u1.latest_activity END AS latest_activity FROM relationships r LEFT JOIN `users` u1 ON r.user1 = u1.id LEFT JOIN `users` u2 ON r.user2 = u2.id WHERE r.user1 = ? OR r.user2 = ? ORDER BY `status` DESC;";

    @Data
    public class Relation {
        public int id;
        public String name;
        public long latest_activity;
        public String relationship_status;
        public UserInfoObject user;
        public boolean supporter = false;
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        if (req.queryParams("info") != null) {
            shiina.data.put("info", req.queryParams("info"));
        }

        if (req.queryParams("error") != null) {
            shiina.data.put("error", req.queryParams("error"));
        }

        ResultSet relations = shiina.mysql.Query(RELATION_QUERY, shiina.user.id, shiina.user.id, shiina.user.id,
                shiina.user.id, shiina.user.id, shiina.user.id);
        ArrayList<Relation> relationsList = new ArrayList<>();
        while (relations.next()) {
            Relation relation = new Relation();
            relation.id = relations.getInt("id");
            relation.name = relations.getString("name");
            relation.latest_activity = relations.getLong("latest_activity");
            relation.relationship_status = relations.getString("status");
            relation.user = new UserInfoObject(relation.id);
            if (Visibility.isRestricted(relation.user.priv)) {
                continue; // restricted players don't show up in friend lists
            }
            if (PermissionHelper.hasPrivileges(relation.user.priv, PermissionHelper.Privileges.SUPPORTER)) {
                relation.user.groups.add(ShiinaSupporterBadge.getInstance().getGroup());
                relation.setSupporter(true);
            }
            relationsList.add(relation);
        }
        shiina.data.put("seo", new SEOBuilder("Relations", App.customization.get("homeDescription").toString()));
        shiina.data.put("relations", relationsList);
        return renderTemplate("user/relations.html", shiina, res, req);
    }

}
