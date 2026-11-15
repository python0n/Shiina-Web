package dev.osunolimits.routes.get.clans;

import java.sql.ResultSet;

import dev.osunolimits.api.ClanQuery;
import dev.osunolimits.api.ClanQuery.SingleClanResponse;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.Embed;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.utils.Validation;
import lombok.Data;
import spark.Request;
import spark.Response;

public class Clan extends Shiina {

    private final String clanRelCheck = "SELECT CASE WHEN u.clan_id != 0 THEN 'true' ELSE 'false' END AS clan_id_check, CASE WHEN u.clan_id = ? THEN 'true' ELSE 'false' END AS user_in_this_clan, CASE WHEN EXISTS (SELECT 1 FROM sh_clan_pending WHERE userid = ? AND clanid = ?) THEN 'true' ELSE 'false' END AS in_sh_clan_pending, CASE WHEN EXISTS (SELECT 1 FROM sh_clan_denied WHERE userid = ? AND clanid = ?) THEN 'true' ELSE 'false' END AS in_sh_clan_denied FROM users u WHERE u.id = ?;";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);
        shiina.data.put("actNav", 4);

        int mode = 0;
        if (req.queryParams("mode") != null && Validation.isNumeric(req.queryParams("mode"))) {
            mode = Integer.parseInt(req.queryParams("mode"));
        }

        Integer id = null;
        if (req.params("id") != null && Validation.isNumeric(req.params("id"))) {
            id = Integer.parseInt(req.params("id"));
        }

        if(id == null) {
            return notFound(res, shiina);
        }

        if(shiina.loggedIn) {
            ResultSet clanRelCheckRS = shiina.mysql.Query(clanRelCheck, id, shiina.user.id, id, shiina.user.id, id, shiina.user.id);
            while(clanRelCheckRS.next()) {
                ClanRel clanRel = new ClanRel();
                clanRel.clanIdCheck = clanRelCheckRS.getBoolean("clan_id_check");
                clanRel.userInThisClan = clanRelCheckRS.getBoolean("user_in_this_clan");
                clanRel.inShClanPending = clanRelCheckRS.getBoolean("in_sh_clan_pending");
                clanRel.inShClanDenied = clanRelCheckRS.getBoolean("in_sh_clan_denied");
                shiina.data.put("clanRel", clanRel);
            }
        }

        shiina.data.put("mode", mode);

        ClanQuery clanQuery = new ClanQuery(shiina.mysql);

        SingleClanResponse response = clanQuery.getClan(mode, id);

        if(response == null) {
            return notFound(res, shiina);
        }

        var members = clanQuery.getMembers(id);
        shiina.data.put("seo", new SEOBuilder(response.getName() + " [" + response.getTag() + "]",
                Embed.text(shiina, "seo.clan", "server", App.customization.get("serverName"), "members", members.size())));
        shiina.data.put("clan", response);
        shiina.data.put("members", members);
        shiina.data.put("activity", clanQuery.getClanActivity(id, mode));
        return renderTemplate("clans/clan.html", shiina, res, req);
    }

    @Data
    public class ClanRel {
        private boolean clanIdCheck;
        private boolean userInThisClan;
        private boolean inShClanPending;
        private boolean inShClanDenied;
    }
    
}
