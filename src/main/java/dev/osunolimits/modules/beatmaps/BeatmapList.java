package dev.osunolimits.modules.beatmaps;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.common.MySQL;
import dev.osunolimits.utils.osu.DiffColour;
import lombok.Data;

/**
 * The beatmap listing, one row per mapset, read straight from the database so it can search
 * several fields at once, filter server uploads (BSS) and maps played here, and sort.
 */
public class BeatmapList {

    public static final int BSS_FIRST_SET = 1073741824;
    /** status filter value for "everything with a leaderboard": ranked, approved, qualified, loved */
    public static final int STATUS_LEADERBOARD = -2;
    public static final int STATUS_ALL = 999;

    @Data
    public static class Diff {
        private int id;
        private int mode;
        private double stars;
        private String colour;
    }

    @Data
    public static class MapSet {
        private int set_id;
        private int id;
        private String artist;
        private String title;
        private String creator;
        private int status;
        private String last_update;
        private int plays;
        private int passes;
        private double diff;
        private boolean bss;
        private List<Diff> diffs = new ArrayList<>();
    }

    @Data
    public static class Page {
        private List<MapSet> sets = new ArrayList<>();
        private int total;
    }

    public static Page fetch(MySQL mysql, String search, int status, int mode, boolean bssOnly, boolean playedOnly,
            String sort, int page, int pageSize) throws Exception {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (status == STATUS_LEADERBOARD) {
            where.append(" AND m.status IN (2, 3, 4, 5)");
        } else if (status != STATUS_ALL) {
            where.append(" AND m.status = ?");
            args.add(status);
        }
        if (mode >= 0 && mode <= 3) {
            where.append(" AND m.mode = ?");
            args.add(mode);
        }
        if (bssOnly) {
            where.append(" AND m.set_id >= ").append(BSS_FIRST_SET);
        }
        if (playedOnly) {
            where.append(" AND m.plays > 0");
        }
        if (search != null && !search.isBlank()) {
            // every word has to appear in the artist, title, mapper or difficulty name
            for (String word : search.trim().split("\\s+")) {
                if (word.isEmpty() || args.size() > 40) continue;
                where.append(" AND CONCAT_WS(' ', m.artist, m.title, m.creator, m.version) LIKE ?");
                args.add("%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
        }

        String order;
        switch (sort == null ? "" : sort) {
            case "plays": order = "plays DESC, last_update DESC"; break;
            case "stars": order = "diff DESC, last_update DESC"; break;
            case "stars_asc": order = "diff ASC, last_update DESC"; break;
            case "title": order = "title ASC, last_update DESC"; break;
            case "artist": order = "artist ASC, last_update DESC"; break;
            default: order = "last_update DESC"; break;
        }

        Page result = new Page();
        ResultSet count = mysql.Query("SELECT COUNT(DISTINCT m.set_id) AS c FROM maps m" + where, args.toArray());
        if (count != null && count.next()) {
            result.setTotal(count.getInt("c"));
        }

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add(Math.max(0, (page - 1) * pageSize));
        ResultSet rs = mysql.Query(
            "SELECT m.set_id, MIN(m.id) AS id, MIN(m.artist) AS artist, MIN(m.title) AS title, MIN(m.creator) AS creator, " +
            "MAX(m.status) AS status, MAX(m.last_update) AS last_update, SUM(m.plays) AS plays, SUM(m.passes) AS passes, " +
            "MAX(m.diff) AS diff, GROUP_CONCAT(CONCAT(m.id, ':', m.mode, ':', m.diff) ORDER BY m.mode, m.diff SEPARATOR ',') AS diffs " +
            "FROM maps m" + where + " GROUP BY m.set_id ORDER BY " + order + " LIMIT ? OFFSET ?",
            pageArgs.toArray());
        while (rs != null && rs.next()) {
            MapSet set = new MapSet();
            set.setSet_id(rs.getInt("set_id"));
            set.setId(rs.getInt("id"));
            set.setArtist(rs.getString("artist"));
            set.setTitle(rs.getString("title"));
            set.setCreator(rs.getString("creator"));
            set.setStatus(rs.getInt("status"));
            set.setLast_update(rs.getString("last_update"));
            set.setPlays(rs.getInt("plays"));
            set.setPasses(rs.getInt("passes"));
            set.setDiff(rs.getDouble("diff"));
            set.setBss(set.getSet_id() >= BSS_FIRST_SET);
            String diffs = rs.getString("diffs");
            if (diffs != null) {
                for (String part : diffs.split(",")) {
                    String[] p = part.split(":");
                    if (p.length < 3) continue;
                    Diff d = new Diff();
                    d.setId(Integer.parseInt(p[0]));
                    d.setMode(Integer.parseInt(p[1]));
                    d.setStars(Double.parseDouble(p[2]));
                    d.setColour(DiffColour.of(d.getStars()));
                    set.getDiffs().add(d);
                }
                // the tile links to the easiest difficulty of the first mode
                if (!set.getDiffs().isEmpty()) {
                    set.setId(set.getDiffs().get(0).getId());
                }
            }
            result.getSets().add(set);
        }
        return result;
    }
}
