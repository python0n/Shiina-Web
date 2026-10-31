package dev.osunolimits.routes.post.settings.customization;


import dev.osunolimits.utils.Visibility;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.pubsubs.SyncedAction;
import dev.osunolimits.plugins.events.actions.OnUserNameChangeEvent;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleNameChange extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
       ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/settings/customization");
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return redirect(res, shiina, "/settings?error=Restricted accounts cannot change their profile");
        }

        if(!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.SUPPORTER)) {
            return redirect(res, shiina, "/settings?error=You do not have permission to do this");
        }

        String newName = req.queryParams("newname");
        if (newName == null || newName.isEmpty() || !newName.matches("^(?! )[\\w\\[\\] -]{2,15}(?<! )$")) {
            return redirect(res, shiina, "/settings?error=Invalid name");
        }

        String usernameCheckSql = "SELECT `id` FROM `users` WHERE `name` = ?";
        if(shiina.mysql.Query(usernameCheckSql, newName).next()) {
            return redirect(res, shiina, "/settings?error=Name already taken");
        }
        
        SyncedAction.changeName(shiina.user.id, newName);
        
        new OnUserNameChangeEvent(shiina.user.id, shiina.user.name, newName).callListeners();

        return redirect(res, shiina, "/settings/customization?info=Name was changed successfully");
    }
}
