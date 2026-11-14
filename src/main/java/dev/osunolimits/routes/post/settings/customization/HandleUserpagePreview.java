package dev.osunolimits.routes.post.settings.customization;

import java.util.concurrent.ConcurrentHashMap;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.UserpageRenderer;
import dev.osunolimits.utils.Visibility;
import spark.Request;
import spark.Response;

/** Preview tab of the userpage editor: returns the HTML the profile would show. */
public class HandleUserpagePreview extends Shiina {

    private static final long MIN_INTERVAL_MS = 500;
    private static final ConcurrentHashMap<Integer, Long> lastPreview = new ConcurrentHashMap<>();

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        res.type("text/html; charset=utf-8");
        res.header("Cache-Control", "no-store");

        if (!shiina.loggedIn || Visibility.isRestricted(shiina.user.priv)) {
            res.status(403);
            shiina.mysql.close();
            return "";
        }

        long now = System.currentTimeMillis();
        Long last = lastPreview.put(shiina.user.id, now);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            res.status(429);
            shiina.mysql.close();
            return "";
        }
        if (lastPreview.size() > 10000) {
            lastPreview.clear();
        }

        String userpage = req.queryParams("userpage");
        if (userpage == null) {
            userpage = "";
        }
        userpage = userpage.replace("\r\n", "\n").strip();
        if (userpage.length() > UserpageRenderer.MAX_LENGTH) {
            res.status(413);
            shiina.mysql.close();
            return "";
        }
        return raw(res, shiina, UserpageRenderer.render(userpage));
    }
}
