package dev.osunolimits.routes.get;

import java.io.File;
import java.nio.file.Files;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

public class ServeBadge extends Shiina {
    private static final String BADGE_DIR = "data/badges";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        String name = req.params("name");
        if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,80}")) {
            return notFound(res, shiina);
        }
        File f = new File(BADGE_DIR, name);
        if (!f.exists() || !f.getCanonicalPath().startsWith(new File(BADGE_DIR).getCanonicalPath())) {
            return notFound(res, shiina);
        }
        res.type("image/png");
        res.header("Cache-Control", "public, max-age=604800");
        byte[] data = Files.readAllBytes(f.toPath());
        res.raw().getOutputStream().write(data);
        res.raw().getOutputStream().flush();
        return res.raw();
    }
}
