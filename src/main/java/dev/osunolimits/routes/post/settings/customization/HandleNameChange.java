package dev.osunolimits.routes.post.settings.customization;


import dev.osunolimits.utils.AccountRules;
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
        String nameError = AccountRules.checkName(shiina.mysql, newName, shiina.user.id);
        if (nameError != null) {
            return redirect(res, shiina, "/settings?error=" + java.net.URLEncoder.encode(nameError, java.nio.charset.StandardCharsets.UTF_8));
        }
        
        SyncedAction.changeName(shiina.user.id, newName);
        
        new OnUserNameChangeEvent(shiina.user.id, shiina.user.name, newName).callListeners();

        return redirect(res, shiina, "/settings/customization?info=Name was changed successfully");
    }
}
