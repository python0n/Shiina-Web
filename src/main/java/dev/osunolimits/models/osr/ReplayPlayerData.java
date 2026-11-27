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
            if ((o.getHitSound() & (2 | 4 | 8)) != 0) m.put("hs", o.getHitSound() & (2 | 4 | 8)); // whistle, finish, clap
            if (o.isSpinner()) {
                m.put("k", "p");
            } else {
                combo++;
                m.put("n", combo);
                m.put("c", colour % 4);
                if (ni < notes.size()) {
                    var nr = notes.get(ni);
                    m.put("v", nr.getVerdict());
                    if (nr.getSliderResult() != null) m.put("sr", nr.getSliderResult());
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
        out.put("mode", 0);
        out.put("radius", r1((float) (54.4 - 4.48 * cs)));
        out.put("preempt", Math.round(ar > 5 ? 1200 - 750 * (ar - 5) / 5 : 1200 + 600 * (5 - ar) / 5));
        out.put("rate", ReplayAnalyzer.clockRate(mods));
        out.put("hidden", (mods & 8) != 0);
        out.put("flashlight", (mods & 1024) != 0);
        out.put("nightcore", (mods & 512) != 0);
        int[] w = OsuFileParser.TimingPoint.getHitWindows((mods & 16) != 0 ? Math.min(osu.getOd() * 1.4f, 10f) : (mods & 2) != 0 ? osu.getOd() * 0.5f : osu.getOd());
        out.put("windows", w);
        out.put("meter", realWindows(analysis));
        out.put("objects", objects);
        out.put("frames", flat);
        return GSON.toJson(out);
    }

    /** Player data for any analyzed mode: 0 osu! (also relax, autopilot), 1 taiko, 3 mania. */
    public static String json(int mode, OsrData osr, OsuData osu, int mods, AnalysisResult analysis) {
        return switch (mode) {
            case 1 -> taiko(osr, osu, mods, analysis);
            case 2 -> fruits(osr, osu, mods, analysis);
            case 3 -> mania(osr, osu, mods, analysis);
            default -> json(osr, osu, mods, analysis);
        };
    }

    /** Hit windows in real ms, narrowest first, for the hit error meter. */
    private static List<Double> realWindows(AnalysisResult analysis) {
        List<Double> w = new ArrayList<>();
        if (analysis != null) for (Map<String, Object> m : analysis.getHitWindows()) w.add(((Number) m.get("ms")).doubleValue());
        return w;
    }

    private static Map<String, Object> verdict(Map<String, Object> m, ReplayAnalyzer.NoteResult nr) {
        m.put("v", nr.getVerdict());
        boolean hit = !("miss".equals(nr.getVerdict()) || "wrong".equals(nr.getVerdict()) || "slider_miss".equals(nr.getVerdict()));
        if (hit) m.put("te", Math.round(nr.getTimingError() * 10.0) / 10.0);
        return m;
    }

    /** Frames as one flat array: time, keys (taiko) or pressed columns (mania). */
    private static long[] keyFrames(OsrData osr, boolean columns) {
        var frames = osr.getFrames();
        long[] flat = new long[frames.size() * 2];
        int j = 0;
        for (var f : frames) {
            flat[j++] = f.getTimeMs();
            flat[j++] = columns ? Math.max(0, (long) f.getX()) : f.getKeys();
        }
        return flat;
    }

    private static Map<String, Object> base(int mode, int mods, AnalysisResult analysis) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("rate", ReplayAnalyzer.clockRate(mods));
        out.put("nightcore", (mods & 512) != 0);
        out.put("hidden", (mods & 8) != 0);
        out.put("flashlight", (mods & 1024) != 0);
        out.put("meter", realWindows(analysis));
        return out;
    }

    private static String mania(OsrData osr, OsuData osu, int mods, AnalysisResult analysis) {
        var all = osu.getHitObjects();
        int keys = Math.max(1, Math.min(18, Math.round(osu.getCs())));
        List<ReplayAnalyzer.NoteResult> notes = analysis.getNoteResults();
        List<Map<String, Object>> objects = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            var o = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", o.getTimeMs());
            if (o.isHold()) m.put("e", o.getEndTimeMs());
            int c = (int) Math.floor(o.getX() * keys / 512f);
            m.put("c", Math.max(0, Math.min(keys - 1, c)));
            if (i < notes.size()) {
                verdict(m, notes.get(i));
                if (notes.get(i).getTailVerdict() != null) m.put("tv", notes.get(i).getTailVerdict());
            }
            objects.add(m);
        }
        Map<String, Object> out = base(3, mods, analysis);
        out.put("keys", keys);
        out.put("objects", objects);
        out.put("frames", keyFrames(osr, true));
        return GSON.toJson(out);
    }

    private static String taiko(OsrData osr, OsuData osu, int mods, AnalysisResult analysis) {
        List<ReplayAnalyzer.NoteResult> notes = analysis.getNoteResults();
        List<Map<String, Object>> objects = new ArrayList<>();
        int ni = 0;
        for (var o : osu.getHitObjects()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", o.getTimeMs());
            if (o.isCircle()) {
                m.put("k", (o.getHitSound() & (2 | 8)) != 0 ? "kat" : "don");
                if ((o.getHitSound() & 4) != 0) m.put("b", true);
                if (ni < notes.size()) verdict(m, notes.get(ni++));
            } else {
                // drumroll (slider) or swell (spinner): shown, not judged
                m.put("k", o.isSpinner() ? "swell" : "roll");
                m.put("e", o.getEndTimeMs());
            }
            objects.add(m);
        }
        Map<String, Object> out = base(1, mods, analysis);
        out.put("objects", objects);
        out.put("frames", keyFrames(osr, false));
        return GSON.toJson(out);
    }

    private static String fruits(OsrData osr, OsuData osu, int mods, AnalysisResult analysis) {
        var objects = CatchAnalyzer.objects(osu, mods);
        List<ReplayAnalyzer.NoteResult> notes = analysis.getNoteResults();
        List<Map<String, Object>> out = new ArrayList<>();
        int ni = 0;
        for (var o : objects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", Math.round(o.time));
            m.put("x", r1(o.x()));
            m.put("k", switch (o.kind) { case FRUIT -> "f"; case DROPLET -> "d"; case TINY -> "y"; default -> "b"; });
            if (o.hyperDash) m.put("h", true);
            if (o.kind != CatchBeatmap.Kind.BANANA && ni < notes.size()) {
                var nr = notes.get(ni++);
                m.put("v", nr.getVerdict());
                if (nr.getClickTimeMs() != 0) m.put("te", Math.round(nr.getTimingError() * 10.0) / 10.0);
            }
            out.add(m);
        }
        // frames: time, catcher x (x10), keys (1 = dash)
        var frames = osr.getFrames();
        long[] flat = new long[frames.size() * 3];
        int j = 0;
        for (var f : frames) {
            flat[j++] = f.getTimeMs();
            flat[j++] = Math.round(f.getX() * 10);
            flat[j++] = f.getKeys();
        }
        float cs = ReplayAnalyzer.applyMods(osu.getCs(), mods, 1.3f);
        float ar = ReplayAnalyzer.applyMods(osu.getAr(), mods, 1.4f);
        Map<String, Object> data = base(2, mods, analysis);
        data.put("half", r1((float) (CatchBeatmap.catchWidth(cs) / 2)));
        data.put("scale", (1.0f - 0.7f * (cs - 5) / 5));
        data.put("preempt", Math.round(ar > 5 ? 1200 - 750 * (ar - 5) / 5 : 1200 + 600 * (5 - ar) / 5));
        data.put("objects", out);
        data.put("frames", flat);
        return GSON.toJson(data);
    }

    private static double r1(float v) {
        return Math.round(v * 10) / 10.0;
    }

}
