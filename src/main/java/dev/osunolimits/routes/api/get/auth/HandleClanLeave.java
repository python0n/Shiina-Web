package dev.osunolimits.routes.api.get.auth;

import dev.osunolimits.modules.pubsubs.SyncedAction;
import java.sql.ResultSet;

import dev.osunolimits.models.Clan;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.clans.OnUserLeaveClanEvent;
import spark.Request;
import spark.Response;

public class HandleClanLeave extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null) {
            return notFound(res, shiina);
        }

        ResultSet clanCheck = shiina.mysql.Query("SELECT u.`clan_id`, c.`name`, c.`tag` FROM `users` u LEFT JOIN `clans` c ON u.`clan_id` = c.`id` WHERE u.`id` = ?", shiina.user.id);
        if (clanCheck.next()) {

            int clanId = clanCheck.getInt("clan_id");
            if (clanId == 0) {
                return raw(res, shiina, "not_in_clan");
            }

            Clan clan = new Clan(
                clanCheck.getInt("clan_id"),
                clanCheck.getString("name"),
                clanCheck.getString("tag")
            );
            
            // Trigger event
            OnUserLeaveClanEvent event = new OnUserLeaveClanEvent(clan, shiina.user.id);
            event.callListeners();
        }

        shiina.mysql.Exec("UPDATE `users` SET `clan_id`=0,`clan_priv`=0 WHERE `id` = ?", shiina.user.id);
        SyncedAction.clanChanged(shiina.user.id);

        return raw(res, shiina, "success");
    }
}
