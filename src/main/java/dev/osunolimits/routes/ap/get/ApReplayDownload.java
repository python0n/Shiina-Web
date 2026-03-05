package dev.osunolimits.routes.ap.get;

import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class ApReplayDownload extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn || shiina.user.id != 3) {
            res.status(403);
            return "Forbidden";
        }

        String idStr = req.queryParams("id");
        if (idStr == null) {
            res.status(400);
            return "Bad Request";
        }

        long pendingId = Long.parseLong(idStr);

        ResultSet rs = shiina.mysql.Query(
                "SELECT osr_path, player_name, map_md5 FROM pending_replays WHERE id = ?", pendingId);

        if (!rs.next()) {
            res.status(404);
            return "Not Found";
        }

        String osrPath = rs.getString("osr_path");
        String playerName = rs.getString("player_name");

        File f = new File(osrPath);
        if (!f.exists()) {
            res.status(404);
            return "File does not exist on disk.";
        }

        byte[] bytes = Files.readAllBytes(f.toPath());

        res.raw().setContentType("application/octet-stream");
        res.raw().setHeader("Content-Disposition",
                "attachment; filename=\"" + playerName + "_" + pendingId + ".osr\"");
        res.raw().setContentLength(bytes.length);
        res.raw().getOutputStream().write(bytes);
        res.raw().getOutputStream().flush();

        return "";
    }
}
