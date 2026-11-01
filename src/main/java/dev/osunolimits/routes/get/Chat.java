package dev.osunolimits.routes.get;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

/** /chat - the in-game chat in the browser (channels, PMs, BanchoBot, !mp rooms). */
public class Chat extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/chat");
        }

        shiina.data.put("actNav", 0);
        shiina.data.put("seo", new SEOBuilder("Chat", App.customization.get("homeDescription").toString()));
        shiina.data.put("chatAllowed",
                PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.UNRESTRICTED));

        // where the browser opens the WebSocket; empty = same host as the page (/ws/chat via nginx)
        String wsUrl = App.env.get("CHAT_WS_URL");
        shiina.data.put("chatWsUrl", wsUrl == null ? "" : wsUrl);

        String openWith = req.queryParams("with");
        shiina.data.put("chatOpenWith", openWith != null && openWith.matches("[A-Za-z0-9_\\- \\[\\]]{1,32}") ? openWith : "");

        return renderTemplate("chat.html", shiina, res, req);
    }
}
