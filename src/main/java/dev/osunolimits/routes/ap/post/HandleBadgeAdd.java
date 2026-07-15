package dev.osunolimits.routes.ap.post;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.servlet.MultipartConfigElement;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class HandleBadgeAdd extends Shiina {
    private static final String BADGE_DIR = "data/badges";
    private final MultipartConfigElement cfg;
    private static final int MAX = (Integer.parseInt(App.env.get("MAXREQUESTSIZE"))) * 1024 * 1024;

    public HandleBadgeAdd() {
        cfg = new MultipartConfigElement(".temp/", MAX, MAX, 1);
        File d = new File(BADGE_DIR);
        if (!d.exists()) d.mkdirs();
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        if (!shiina.loggedIn) { res.redirect("/login"); return notFound(res, shiina); }
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
            res.redirect("/"); return notFound(res, shiina);
        }
        req.raw().setAttribute("org.eclipse.jetty.multipartConfig", cfg);
        String userId = req.raw().getParameter("userid");
        String caption = req.raw().getParameter("caption");
        String date = req.raw().getParameter("awarded_date");
        String link = req.raw().getParameter("link");
        if (userId == null || !Validation.isNumeric(userId) || caption == null || caption.isBlank()) {
            res.redirect("/ap/user?id=" + (userId == null ? "" : userId) + "&error=Brak userid/caption");
            return notFound(res, shiina);
        }
        try {
            var part = req.raw().getPart("badge");
            if (part == null || part.getSubmittedFileName() == null
                    || !part.getSubmittedFileName().toLowerCase().endsWith(".png")) {
                res.redirect("/ap/user?id=" + userId + "&error=Wgraj plik .png");
                return notFound(res, shiina);
            }
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".png";
            Path dest = Path.of(BADGE_DIR, fileName);
            try (InputStream in = part.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            String safeDate = (date == null) ? "" : date.trim();
            String safeLink = (link == null) ? "" : link.trim();
            shiina.mysql.Exec(
                "INSERT INTO user_badges (userid, image, caption, awarded_date, link, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, (SELECT COALESCE(MAX(t.sort_order),0)+1 FROM (SELECT sort_order FROM user_badges WHERE userid = ?) t))",
                Integer.parseInt(userId), fileName, caption.trim(), safeDate, safeLink, Integer.parseInt(userId));
            res.redirect("/ap/user?id=" + userId + "&info=Badge dodany");
        } catch (Exception e) {
            res.redirect("/ap/user?id=" + userId + "&error=Blad uploadu: " + e.getMessage());
        }
        return notFound(res, shiina);
    }
}
