package dev.osunolimits.routes.api.get.auth;

import dev.osunolimits.utils.Visibility;
import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.clans.OnUserRequestJoinClanEvent;
import dev.osunolimits.utils.Validation;
import spark.Request;
import spark.Response;

public class HandleClanRequest extends Shiina {
    private final String clanRelCheck = "SELECT CASE WHEN u.clan_id != 0 THEN 'true' ELSE 'false' END AS clan_id_check, CASE WHEN u.clan_id = ? THEN 'true' ELSE 'false' END AS user_in_this_clan, CASE WHEN EXISTS (SELECT 1 FROM sh_clan_pending WHERE userid = ? AND clanid = ?) THEN 'true' ELSE 'false' END AS in_sh_clan_pending, CASE WHEN EXISTS (SELECT 1 FROM sh_clan_denied WHERE userid = ? AND clanid = ?) THEN 'true' ELSE 'false' END AS in_sh_clan_denied FROM users u WHERE u.id = ?;";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        if (shiina.user == null) {
            return notFound(res, shiina);
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return notFound(res, shiina);
        }

        String action = null;
        if (req.queryParams("action") != null) {
            action = req.queryParams("action");
        }

        Integer userid = shiina.user.id;

        Integer clanid = null;
        if (req.queryParams("clanid") != null && Validation.isNumeric(req.queryParams("clanid"))) {
            clanid = Integer.parseInt(req.queryParams("clanid"));
        }

        if (action == null || clanid == null) {
            return notFound(res, shiina);
        }

        switch (action.toUpperCase()) {
            case "REQUEST":
                ResultSet clanRelCheckRS = shiina.mysql.Query(clanRelCheck, clanid, userid, clanid, userid, clanid, userid);    
                if (clanRelCheckRS.next()) {
                    if (clanRelCheckRS.getBoolean("clan_id_check") || clanRelCheckRS.getBoolean("user_in_this_clan")
                            || clanRelCheckRS.getBoolean("in_sh_clan_pending")
                            || clanRelCheckRS.getBoolean("in_sh_clan_denied")) {
                        return notFound(res, shiina);
                    }

                    shiina.mysql.Exec("INSERT INTO `sh_clan_pending` (`userid`, `clanid`, `request_time`) VALUES (?, ?, ?)", userid, clanid, System.currentTimeMillis() / 1000);
                    new OnUserRequestJoinClanEvent(clanid, userid, shiina.user.id).callListeners();
                }
                break;

            case "REVOKE":
                shiina.mysql.Exec("DELETE FROM `sh_clan_pending` WHERE `userid` = ? AND `clanid` = ?", userid, clanid);
                break;
            default:
                return notFound(res, shiina);
        }
        return raw(res, shiina, "success");
    }

}
