package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import java.util.Locale;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.pubsubs.SyncedAction;
import dev.osunolimits.plugins.events.actions.OnUserFlagChangeEvent;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleFlagChange extends Shiina {

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

        int userId = shiina.user.id;
        String flag = req.queryParams("newCountry");

        if(flag == null || flag.isEmpty()) {
            return redirect(res, shiina, "/settings?error=Invalid flag");
        }

        String[] countryCodes = Locale.getISOCountries();
        boolean valid = false;
        for (String countryCode : countryCodes) {
            if(countryCode.equalsIgnoreCase(flag)) {
                valid = true;
                break;
            }
        }

        if(!valid) {
            return redirect(res, shiina, "/settings?error=Invalid flag");
        }

        SyncedAction.changeCountryFlag(userId, flag);
        
        new OnUserFlagChangeEvent(userId, flag).callListeners();

        return redirect(res, shiina, "/settings/customization?info=Flag was changed, it may take a few minutes to update fully");
    }
}
