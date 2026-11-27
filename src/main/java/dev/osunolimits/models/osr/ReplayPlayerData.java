package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;

/** Data for the replay player (static/js/replay-player.js), shared by the analyzer and score pages. */
public final class ReplayPlayerData {

    private static final Gson GSON = new Gson();

    private ReplayPlayerData() {}

    /**
     * Everything the replay player on the page needs (osu!standard): objects where the game
     * shows them (stacks, HR), slider paths, cursor frames and the analyzer's verdict per object
     * (pass null as analysis to leave the verdicts out).
     */
    public static String json(OsrData osr, OsuData osu, int mods, AnalysisResult analysis) {
        float cs = (mods & 16) != 0 ? Math.min(osu.getCs() * 1.3f, 10f) : (mods & 2) != 0 ? osu.getCs() * 0.5f : osu.getCs();
        float ar = (mods & 16) != 0 ? Math.min(osu.getAr() * 1.4f, 10f) : (mods & 2) != 0 ? osu.getAr() * 0.5f : osu.getAr();
        boolean hardRock = (mods & 16) != 0;
        float[][] pos = ReplayAnalyzer.placedPositions(osu, mods);
        var all = osu.getHitObjects();
        List<Map<String, Object>> objects = new ArrayList<>();
        int combo = 0, colour = 0, ni = 0;
        // without an analysis (relax, autopilot) the player shows no verdicts
        List<ReplayAnalyzer.NoteResult> notes = analysis != null ? analysis.getNoteResults() : List.of();
        for (int i = 0; i < all.size(); i++) {
            var o = all.get(i);
            if (i == 0 || o.isNewCombo()) {
                if (i > 0) colour += 1 + ((o.getType() >> 4) & 7);
                combo = 0;
            }
            if (o.isSpinner()) { combo = 0; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", o.getTimeMs());
            m.put("e", o.getEndTimeMs());
            m.put("x", r1(pos[i][0]));
            m.put("y", r1(pos[i][1]));
            if (o.isSpinner()) {
                m.put("k", "p");
            } else {
                combo++;
                m.put("n", combo);
                m.put("c", colour % 4);
                if (ni < notes.size()) {
                    var nr = notes.get(ni);
                    m.put("v", nr.getVerdict());
                    if (nr.getClickTimeMs() != 0 || nr.getTimingError() != 0) m.put("te", Math.round(nr.getTimingError() * 10.0) / 10.0);
                    ni++;
                }
                if (o.isSlider() && o.getPath() != null) {
                    m.put("k", "s");
                    m.put("r", o.getSlides());
                    float dx = pos[i][0] - o.getX();
                    float dy = pos[i][1] - (hardRock ? 384f - o.getY() : o.getY());
                    List<float[]> pts = o.getPath().drawPoints(120);
                    List<Object> path = new ArrayList<>();
                    for (float[] p : pts) {
                        path.add(r1(p[0] + dx));
                        path.add(r1((hardRock ? 384f - p[1] : p[1]) + dy));
                    }
                    m.put("p", path);
                } else {
                    m.put("k", "c");
                }
            }
            objects.add(m);
        }
        // frames as one flat array: time, x, y, keys
        var frames = osr.getFrames();
        long[] flat = new long[frames.size() * 4];
        int j = 0;
        for (var f : frames) {
            flat[j++] = f.getTimeMs();
            flat[j++] = Math.round(f.getX() * 10);
            flat[j++] = Math.round(f.getY() * 10);
            flat[j++] = f.getKeys();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("radius", r1((float) (54.4 - 4.48 * cs)));
        out.put("preempt", Math.round(ar > 5 ? 1200 - 750 * (ar - 5) / 5 : 1200 + 600 * (5 - ar) / 5));
        out.put("rate", ReplayAnalyzer.clockRate(mods));
        out.put("hidden", (mods & 8) != 0);
        out.put("nightcore", (mods & 512) != 0);
        int[] w = OsuFileParser.TimingPoint.getHitWindows((mods & 16) != 0 ? Math.min(osu.getOd() * 1.4f, 10f) : (mods & 2) != 0 ? osu.getOd() * 0.5f : osu.getOd());
        out.put("windows", w);
        out.put("objects", objects);
        out.put("frames", flat);
        return GSON.toJson(out);
    }

    private static double r1(float v) {
        return Math.round(v * 10) / 10.0;
    }

}
