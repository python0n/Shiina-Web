package dev.osunolimits.routes.post;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.ResultSet;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.osr.OsrParser;
import dev.osunolimits.modules.osr.OsrParser.OsrData;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

public class HandleReplaySubmit extends Shiina {

    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final String OSR_DIR = "/mnt/storage/osu/bancho-py-ex/.data/osr/pending/";

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/replays");
        }

        req.raw().setAttribute("org.eclipse.jetty.multipartConfig",
                new MultipartConfigElement("/tmp", MAX_SIZE, MAX_SIZE, (int) MAX_SIZE));

        Part filePart;
        try {
            filePart = req.raw().getPart("replay");
        } catch (Exception e) {
            shiina.data.put("error", "Replay file not found.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        if (filePart == null || filePart.getSize() == 0) {
            shiina.data.put("error", "File is empty.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        if (filePart.getSize() > MAX_SIZE) {
            shiina.data.put("error", "File is too large (max 10MB).");
            return renderTemplate("replays.html", shiina, res, req);
        }

        String filename = filePart.getSubmittedFileName();
        if (filename == null || !filename.toLowerCase().endsWith(".osr")) {
            shiina.data.put("error", "File must be in .osr format.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        byte[] osrBytes = filePart.getInputStream().readAllBytes();

        OsrData osr;
        try {
            osr = OsrParser.parse(osrBytes);
        } catch (Exception e) {
            App.log.warn("Failed to parse osr: " + e.getMessage());
            shiina.data.put("error", "Failed to parse the replay file.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        ResultSet mapRs = shiina.mysql.Query("SELECT title FROM maps WHERE md5 = ?", osr.getMapMd5());
        if (mapRs == null || !mapRs.next()) {
            shiina.data.put("error", "The beatmap for this replay does not exist on the server.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        ResultSet existRs = shiina.mysql.Query(
                "SELECT id FROM pending_replays WHERE userid = ? AND map_md5 = ? AND status = 0",
                String.valueOf(shiina.user.id), osr.getMapMd5());
        if (existRs != null && existRs.next()) {
            shiina.data.put("error", "You already have a pending replay for this map.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        ResultSet scoreRs = shiina.mysql.Query(
                "SELECT id FROM scores WHERE userid = ? AND map_md5 = ? AND online_checksum = ?",
                String.valueOf(shiina.user.id), osr.getMapMd5(), osr.getReplayMd5());
        if (scoreRs != null && scoreRs.next()) {
            shiina.data.put("error", "This score already exists on the server.");
            return renderTemplate("replays.html", shiina, res, req);
        }

        File dir = new File(OSR_DIR);
        if (!dir.exists()) dir.mkdirs();

        String savedFilename = "pending_" + shiina.user.id + "_" + System.currentTimeMillis() + ".osr";
        File outFile = new File(OSR_DIR + savedFilename);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(osrBytes);
        }

        shiina.mysql.Exec(
                "INSERT INTO pending_replays (userid, map_md5, player_name, mode, mods, score, max_combo, acc, " +
                "n300, n100, n50, nmiss, ngeki, nkatu, perfect, grade, replay_md5, osr_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (Integer) shiina.user.id,
                osr.getMapMd5(),
                osr.getPlayerName(),
                (Integer) osr.getMode(),
                (Integer) osr.getMods(),
                (Integer) osr.getScore(),
                (Integer) osr.getMaxCombo(),
                (Double) (double) osr.getAcc(),
                (Integer) osr.getN300(),
                (Integer) osr.getN100(),
                (Integer) osr.getN50(),
                (Integer) osr.getNmiss(),
                (Integer) osr.getNgeki(),
                (Integer) osr.getNkatu(),
                (Integer) (osr.isPerfect() ? 1 : 0),
                osr.getGrade(),
                osr.getReplayMd5(),
                OSR_DIR + savedFilename
        );

        App.log.info("Replay submitted: user=" + shiina.user.id + " map=" + osr.getMapMd5());
        try {
            redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis("localhost", 6379);
            String payload = "{\"user\": \"" + shiina.user.name + "\", \"map_md5\": \"" + osr.getMapMd5() + "\"}";
            jedis.publish("ex:replay_submit", payload);
            jedis.close();
        } catch (Exception e) {
            App.log.warn("Redis replay alert failed: " + e.getMessage());
        }
        return redirect(res, shiina, "/replays?submitted=1");
    }
}
