package dev.osunolimits.routes.get;

import dev.osunolimits.modules.beatmaps.BeatmapList;
import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.OsuConverter;
import spark.Request;
import spark.Response;

public class Beatmaps extends Shiina {

    public static int pageSize = 50;

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 3);
        int page = 1;
        if (req.queryParams("page") != null && Validation.isNumeric(req.queryParams("page")) && Integer.parseInt(req.queryParams("page")) > 0) {
            page = Integer.parseInt(req.queryParams("page"));
        }

        // default like osu-web: only maps with a leaderboard
        int status = BeatmapList.STATUS_LEADERBOARD;
        String statusParam = req.queryParams("status");
        if (statusParam != null && statusParam.matches("-?\\d+")) {
            status = Integer.parseInt(statusParam);
        }

        int mode = 999;
        if (OsuConverter.checkForValidMode(req.queryParams("mode"))) {
            mode = Integer.parseInt(req.queryParams("mode"));
        }

        // the old artist/creator boxes keep working as a search
        String q = req.queryParams("q");
        if ((q == null || q.isBlank()) && req.queryParams("artist") != null) q = req.queryParams("artist");
        if ((q == null || q.isBlank()) && req.queryParams("creator") != null) q = req.queryParams("creator");
        if (q != null && q.length() > 100) q = q.substring(0, 100);

        boolean bss = "1".equals(req.queryParams("bss"));
        boolean played = "1".equals(req.queryParams("played"));
        String sort = req.queryParams("sort");
        if (sort == null || !sort.matches("updated|plays|stars|stars_asc|title|artist")) sort = "updated";

        BeatmapList.Page result = BeatmapList.fetch(shiina.mysql, q, status, mode, bss, played, sort, page, pageSize);
        shiina.data.put("beatmaps", result.getSets());
        shiina.data.put("mode", mode);
        shiina.data.put("status", status);
        shiina.data.put("q", q == null ? "" : q);
        shiina.data.put("bss", bss);
        shiina.data.put("played", played);
        shiina.data.put("sort", sort);
        shiina.data.put("pageSize", pageSize);
        SEOBuilder seo = new SEOBuilder("Beatmap Listing", App.customization.get("homeDescription").toString());
        shiina.data.put("seo", seo);
        shiina.data.put("entries", result.getTotal());
        shiina.data.put("page", page);
        return renderTemplate("beatmaps.html", shiina, res, req);
    }

}
