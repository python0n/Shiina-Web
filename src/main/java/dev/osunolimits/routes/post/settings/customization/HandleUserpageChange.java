package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.UserpageRenderer;
import spark.Request;
import spark.Response;

public class HandleUserpageChange extends Shiina {

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
        int userId = shiina.user.id;
        String userpage = req.queryParams("userpage");
        if (userpage == null) {
            userpage = "";
        }
        userpage = userpage.replace("\r\n", "\n").strip();

        if(userpage.length() > UserpageRenderer.MAX_LENGTH) {
            return redirect(res, shiina, "/settings?error=Userpage is too long (max 10000 characters)");
        }

        // an empty userpage removes it from the profile, like on osu-web
        if (userpage.isEmpty()) {
            shiina.mysql.Exec("DELETE FROM `userpages` WHERE `user_id` = ?", userId);
            return redirect(res, shiina, "/settings/customization?info=Userpage was changed");
        }

        // raw is kept as typed, the template escapes it when it goes back into the editor
        String html = UserpageRenderer.render(userpage);

        ResultSet userpageRs = shiina.mysql.Query("SELECT `id` FROM `userpages` WHERE `user_id` = ?", userId);
        if(userpageRs.next()) {
            shiina.mysql.Exec("UPDATE `userpages` SET `html` = ?, `raw` = ?, `raw_type` = ? WHERE `user_id` = ?", html, userpage, "tiptap", userId);
        }else {
            shiina.mysql.Exec("INSERT INTO `userpages` (`user_id`, `html`, `raw`, `raw_type`) VALUES (?, ?, ?, ?)", userId, html, userpage, "tiptap");
        }

        return redirect(res, shiina, "/settings/customization?info=Userpage was changed");
    }
}
