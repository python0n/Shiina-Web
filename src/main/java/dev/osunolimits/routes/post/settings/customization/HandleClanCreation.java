package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import dev.osunolimits.common.Database;
import dev.osunolimits.common.MySQL;
import dev.osunolimits.models.Clan;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.clans.OnUserClanCreatedEvent;
import dev.osunolimits.modules.XmlConfig;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleClanCreation extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/settings/customization");
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return redirect(res, shiina, "/settings?error=Restricted accounts cannot change their profile");
        }

        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.SUPPORTER) && XmlConfig.getInstance().get("shiina.clans.create-supporter").equals("true")) {
            return redirect(res, shiina, "/settings/customization");
        }

        int userId = shiina.user.id;
        String name = req.queryParams("name");
        String tag = req.queryParams("tag");

        if(name == null || name.isEmpty() || tag == null || tag.isEmpty()) {
            return redirect(res, shiina, "/settings/customization?error=Please provide both a clan name and tag.");
        }

        if(name.length() < 3 || name.length() > 15) {
            return redirect(res, shiina, "/settings/customization?error=Clan name must be between 3 and 15 characters long.");
        }

        if(tag.length() < 1 || tag.length() > 5) {
            return redirect(res, shiina, "/settings/customization?error=Clan tag must be between 1 and 5 characters long.");
        }

        try (MySQL mysql = Database.getConnection()) {
            var checkTagResult = mysql.Query("SELECT * FROM `clans` WHERE `tag` = ?", tag);
            if (checkTagResult.next()) {
                return redirect(res, shiina, "/settings/customization?error=Clan tag is already taken.");
            }

            mysql.Exec("INSERT INTO `clans` (`name`, `tag`, `owner`, `created_at`) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", name, tag, userId);

            var clanResult = mysql.Query("SELECT `id` FROM `clans` WHERE `owner` = ? ORDER BY `created_at` DESC LIMIT 1", userId);
            if (clanResult.next()) {
                int clanId = clanResult.getInt("id");
                mysql.Exec("UPDATE `users` SET `clan_id` = ?, `clan_priv` = 3 WHERE `id` = ?", clanId, userId);

                Clan clan = new Clan(clanId, name, tag);
                new OnUserClanCreatedEvent(clan, userId).callListeners();

                return redirect(res, shiina, "/clan/" + clanId);
            }
        }

        return redirect(res, shiina, "/settings/customization?error=An unknown error occurred while creating the clan.");
    }
}
