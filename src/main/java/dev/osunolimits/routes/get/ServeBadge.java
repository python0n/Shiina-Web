package dev.osunolimits.routes.get;

import java.io.File;
import java.nio.file.Files;
import dev.osunolimits.modules.Shiina;
import spark.Request;
import spark.Response;

public class ServeBadge extends Shiina {
    private static final String BADGE_DIR = "data/badges";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        String name = req.params("name");
        if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,80}")) { res.status(404); return ""; }
        File f = new File(BADGE_DIR, name);
        if (!f.exists() || !f.getCanonicalPath().startsWith(new File(BADGE_DIR).getCanonicalPath())) { res.status(404); return ""; }
        res.type("image/png");
        res.header("Cache-Control", "public, max-age=604800");
        byte[] data = Files.readAllBytes(f.toPath());
        res.raw().getOutputStream().write(data);
        res.raw().getOutputStream().flush();
        return res.raw();
    }
}
