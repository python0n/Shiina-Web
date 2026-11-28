package dev.osunolimits.routes.ap.get;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

/**
 * Anticheat reports saved by bancho (table ac_reports): flags from lastfm.php, flags
 * sent with a score and unusual score tokens. Only hints for the staff - nothing here
 * restricts anyone. A report is closed by marking it as reviewed, with an optional note.
 */
public class AnticheatReports extends Shiina {

    private static final int PAGE_SIZE = 50;

    /** Short names of the client flag bits, the same as in bancho (constants/clientflags.py). */
    private static final String[] FLAG_NAMES = new String[23];
    static {
        FLAG_NAMES[1] = "SPEED_HACK_DETECTED";
        FLAG_NAMES[2] = "INCORRECT_MOD_VALUE";
        FLAG_NAMES[3] = "MULTIPLE_OSU_CLIENTS";
        FLAG_NAMES[4] = "CHECKSUM_FAILURE";
        FLAG_NAMES[5] = "FLASHLIGHT_CHECKSUM_INCORRECT";
        FLAG_NAMES[6] = "OSU_EXECUTABLE_CHECKSUM";
        FLAG_NAMES[7] = "MISSING_PROCESSES_IN_LIST";
        FLAG_NAMES[8] = "FLASHLIGHT_IMAGE_HACK";
        FLAG_NAMES[9] = "SPINNER_HACK";
        FLAG_NAMES[10] = "TRANSPARENT_WINDOW";
        FLAG_NAMES[11] = "FAST_PRESS";
        FLAG_NAMES[12] = "RAW_MOUSE_DISCREPANCY";
        FLAG_NAMES[13] = "RAW_KEYBOARD_DISCREPANCY";
        FLAG_NAMES[14] = "RUN_WITH_LD_FLAG";
        FLAG_NAMES[15] = "CONSOLE_OPEN";
        FLAG_NAMES[16] = "EXTRA_THREADS";
        FLAG_NAMES[17] = "HQ_ASSEMBLY";
        FLAG_NAMES[18] = "HQ_FILE";
        FLAG_NAMES[19] = "REGISTRY_EDITS";
        FLAG_NAMES[20] = "SDL2_LIBRARY";
        FLAG_NAMES[21] = "OPENSSL_LIBRARY";
        FLAG_NAMES[22] = "AQN_MENU_SAMPLE";
    }

    public static List<String> flagNames(long flags) {
        List<String> out = new ArrayList<>();
        for (int bit = 0; bit < 63; bit++) {
            if ((flags & (1L << bit)) == 0) continue;
            out.add(bit < FLAG_NAMES.length && FLAG_NAMES[bit] != null ? FLAG_NAMES[bit] : "bit " + bit);
        }
        return out;
    }

    private static final String[] MOD_NAMES = {"NF", "EZ", "TD", "HD", "HR", "SD", "DT", "RX", "HT", "NC", "FL",
            "AT", "SO", "AP", "PF"};

    static String modsString(int mods) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < MOD_NAMES.length; i++) {
            if ((mods & (1 << i)) == 0) continue;
            if (i == 6 && (mods & (1 << 9)) != 0) continue; // NC includes DT
            if (i == 5 && (mods & (1 << 14)) != 0) continue; // PF includes SD
            b.append(MOD_NAMES[i]);
        }
        return b.length() == 0 ? "NM" : b.toString();
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 23);

        if (!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }
        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
            res.redirect("/");
            return notFound(res, shiina);
        }

        String state = req.queryParamOrDefault("state", "open");
        if (!state.equals("open") && !state.equals("reviewed") && !state.equals("all")) state = "open";
        String source = req.queryParamOrDefault("source", "");
        if (!source.matches("lastfm|score|token")) source = "";
        String userParam = req.queryParamOrDefault("user", "");
        int userId = userParam.matches("\\d{1,9}") ? Integer.parseInt(userParam) : 0;
        int page = 0;
        String pageParam = req.queryParamOrDefault("page", "0");
        if (pageParam.matches("\\d{1,6}")) page = Integer.parseInt(pageParam);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (state.equals("open")) where.append(" AND r.reviewed_at IS NULL");
        if (state.equals("reviewed")) where.append(" AND r.reviewed_at IS NOT NULL");
        if (!source.isEmpty()) { where.append(" AND r.source = ?"); params.add(source); }
        if (userId > 0) { where.append(" AND r.user_id = ?"); params.add(userId); }
        params.add(PAGE_SIZE + 1);
        params.add(page * PAGE_SIZE);

        ResultSet rs = shiina.mysql.Query(
                "SELECT r.id, r.user_id, r.source, r.flags, r.score_id, r.map_md5, r.detail, r.created_at, "
                + "r.reviewed_at, r.note, u.name AS username, rv.name AS reviewer, "
                + "m.id AS map_id, m.artist, m.title, m.version, "
                + "s.mode, s.mods, s.acc, s.pp, s.nmiss, s.grade "
                + "FROM ac_reports r "
                + "LEFT JOIN users u ON u.id = r.user_id "
                + "LEFT JOIN users rv ON rv.id = r.reviewed_by "
                + "LEFT JOIN maps m ON m.md5 = r.map_md5 "
                + "LEFT JOIN scores s ON s.id = r.score_id"
                + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                params.toArray());

        List<Map<String, Object>> reports = new ArrayList<>();
        boolean hasNextPage = false;
        while (rs.next()) {
            if (reports.size() == PAGE_SIZE) { hasNextPage = true; break; }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rs.getLong("id"));
            r.put("userId", rs.getInt("user_id"));
            r.put("username", rs.getString("username") == null ? "#" + rs.getInt("user_id") : rs.getString("username"));
            r.put("source", rs.getString("source"));
            long flags = rs.getLong("flags");
            boolean hasFlags = !rs.wasNull() && flags != 0;
            r.put("flagNames", hasFlags ? flagNames(flags) : new ArrayList<String>());
            long scoreId = rs.getLong("score_id");
            r.put("scoreId", rs.wasNull() ? 0L : scoreId);
            r.put("detail", rs.getString("detail") == null ? "" : rs.getString("detail"));
            r.put("createdAt", rs.getString("created_at"));
            r.put("reviewedAt", rs.getString("reviewed_at") == null ? "" : rs.getString("reviewed_at"));
            r.put("reviewer", rs.getString("reviewer") == null ? "" : rs.getString("reviewer"));
            r.put("note", rs.getString("note") == null ? "" : rs.getString("note"));
            String title = rs.getString("title");
            r.put("map", title == null ? "" : rs.getString("artist") + " - " + title + " [" + rs.getString("version") + "]");
            r.put("mapId", rs.getInt("map_id"));
            boolean hasScore = rs.getObject("mode") != null;
            r.put("hasScore", hasScore);
            if (hasScore) {
                r.put("mode", rs.getInt("mode"));
                r.put("mods", modsString(rs.getInt("mods")));
                r.put("acc", String.format("%.2f", rs.getFloat("acc")));
                r.put("pp", String.format("%.0f", rs.getFloat("pp")));
                r.put("nmiss", rs.getInt("nmiss"));
                r.put("grade", rs.getString("grade"));
            }
            reports.add(r);
        }

        ResultSet cnt = shiina.mysql.Query("SELECT COUNT(*) AS c FROM ac_reports WHERE reviewed_at IS NULL");
        shiina.data.put("openCount", cnt != null && cnt.next() ? cnt.getInt("c") : 0);

        shiina.data.put("reports", reports);
        shiina.data.put("hasNextPage", hasNextPage);
        shiina.data.put("page", page);
        shiina.data.put("state", state);
        shiina.data.put("source", source);
        shiina.data.put("userFilter", userId > 0 ? String.valueOf(userId) : "");
        shiina.data.put("flash", req.queryParamOrDefault("done", ""));
        return renderTemplate("ap/anticheat.html", shiina, res, req);
    }
}
