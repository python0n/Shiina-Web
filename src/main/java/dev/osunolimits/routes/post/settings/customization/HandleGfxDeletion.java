package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import java.io.File;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.plugins.events.actions.OnGfxDeletionEvent;
import spark.Request;
import spark.Response;

public class HandleGfxDeletion extends Shiina {
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


        String action = req.queryParams("action");
        

        if(action == null || action.isEmpty()) {
            return redirect(res, shiina, "/settings/customization?error=Action cannot be empty");
        }
        
        if(action.equals("banner")) {
            File bannerFile = new File("data/banners/" + shiina.user.id + ".png");
            if(bannerFile.exists()) {
                bannerFile.delete();
            } else {
                return redirect(res, shiina, "/settings/customization?error=Banner does not exist");
            }
        }

        if(action.equals("pb")) {
            String avatarPath = App.env.get("AVATARFOLDER");
            File avatarPng = new File(avatarPath + "/" + shiina.user.id + ".png");
            File avatarGif = new File(avatarPath + "/" + shiina.user.id + ".gif");
            App.log.info("Deleting avatar files path: " + avatarPng.getAbsolutePath());
            App.log.info("Deleting avatar files path: " + avatarGif.getAbsolutePath());
            if(avatarPng.exists()) {
                avatarPng.delete();
            }
            if(avatarGif.exists()) {
                avatarGif.delete();
            }
        }

        new OnGfxDeletionEvent(action, shiina.user).callListeners();
        
        return redirect(res, shiina, "/settings/customization?info=Successfully deleted " + action);
    }


}
