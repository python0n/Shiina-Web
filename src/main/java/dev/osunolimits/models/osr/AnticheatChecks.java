package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;
import dev.osunolimits.modules.osr.ReplayAnalyzer.NoteResult;

/**
 * Checks for the staff, shown in the analyzer next to the replay player. They only point at
 * things worth a look; none of them is proof on its own.
 *
 * - timewarp: a stable replay gets a frame about every 16.7 ms of real time, so in song time
 *   the frames are 16.7 ms times the game speed apart (25 ms with DT, 12.5 ms with HT).
 *   Slowing the game down (timewarp) makes them closer together.
 * - unstable rate: very low values without the Relax mod can mean relax software.
 * - key holds: how long each key press lasts; scripts often press with the same length.
 * - snaps (osu!): the cursor jumps out and straight back between three frames right next to
 *   a note, a trace of aim correction.
 *
 * Written for stable replays; lazer replays saved as .osr can use the same checks with
 * their own timewarp numbers.
 */
public final class AnticheatChecks {

    private AnticheatChecks() {}

    private static final double FRAME_MS = 1000.0 / 60.0;
    private static final int MAX_SNAPS = 200;

    public static Map<String, Object> run(OsrData osr, OsuData osu, int mods, AnalysisResult analysis, int mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        double rate = ReplayAnalyzer.clockRate(mods);
        boolean relax = (mods & 128) != 0;
        boolean autopilot = (mods & 8192) != 0;
        out.put("timewarp", timewarp(osr.getFrameDeltaHist(), rate));
        out.put("ur", unstableRate(analysis, mode, relax, autopilot));
        if (mode != 2 && !(mode == 0 && relax)) {
            out.put("holds", holds(osr.getFrames(), mode, rate));
        }
        if (mode == 0 && !autopilot) {
            out.put("snaps", snaps(osr.getFrames(), analysis));
        }
        return out;
    }

    // ---- timewarp ----

    static Map<String, Object> timewarp(int[] hist, double rate) {
        Map<String, Object> t = new LinkedHashMap<>();
        double expected = FRAME_MS * rate;
        // only frames close to the expected spacing: extra frames written on key changes and the
        // 0-2 ms frames of high polling mice / relax software would pull the average down
        int lo = (int) Math.floor(expected * 0.6), hi = (int) Math.ceil(expected * 1.4);
        long n = 0, all = 0, sum = 0, tiny = 0;
        for (int d = 0; d < hist.length; d++) {
            all += hist[d];
            if (d <= 2) tiny += hist[d];
            if (d >= lo && d <= hi && d < hist.length - 1) {
                n += hist[d];
                sum += (long) d * hist[d];
            }
        }
        // median inside the window
        double median = 0;
        long half = n / 2, run = 0;
        for (int d = lo; d <= hi && d < hist.length - 1; d++) {
            run += hist[d];
            if (run > half) { median = d; break; }
        }
        double mean = n > 0 ? (double) sum / n : 0;
        t.put("expected", round1(expected));
        t.put("mean", round1(mean));
        t.put("median", median);
        t.put("frames", n);
        t.put("allFrames", all);
        // many 0-2 ms frames: a high polling rate mouse or relax software adds them; they are left out
        t.put("tinyShare", all > 0 ? round1(100.0 * tiny / all) : 0);
        t.put("ratio", expected > 0 ? round1(100.0 * mean / expected) : 0);
        String verdict;
        if (n < 300) verdict = "few";
        else if (mean < expected * 0.88) verdict = "suspicious"; // normal replays sit at 92-98%
        else verdict = "ok";
        t.put("verdict", verdict);
        // histogram 0..2x expected for the chart
        int max = (int) Math.ceil(expected * 2);
        int[] chart = Arrays.copyOf(hist, Math.min(max + 1, hist.length - 1));
        // the 0-2 ms frames would hide everything else on the chart
        for (int d = 0; d <= 2 && d < chart.length; d++) chart[d] = 0;
        t.put("hist", chart);
        return t;
    }

    // ---- unstable rate ----

    static Map<String, Object> unstableRate(AnalysisResult a, int mode, boolean relax, boolean autopilot) {
        Map<String, Object> u = new LinkedHashMap<>();
        double ur = a.getUnstableRate();
        u.put("value", round1(ur));
        // relax plays have no key presses of their own, so a low UR is expected there
        String verdict;
        if (mode != 0 || relax) verdict = "info";
        else if (ur > 0 && ur < 50) verdict = "suspicious";
        else verdict = "ok";
        u.put("verdict", verdict);
        return u;
    }

    // ---- key holds ----

    static Map<String, Object> holds(List<ReplayFrame> frames, int mode, double rate) {
        Map<String, Object> h = new LinkedHashMap<>();
        int keys = mode == 3 ? 18 : 4;
        long[] down = new long[keys];
        Arrays.fill(down, -1);
        List<Double> lengths = new ArrayList<>();
        int[] perKey = new int[keys];
        for (ReplayFrame f : frames) {
            // mania keeps the pressed columns in x
            int k = mode == 3 ? (int) f.getX() : f.getKeys();
            for (int i = 0; i < keys; i++) {
                boolean pressed = keyPressed(k, i, mode);
                if (pressed && down[i] < 0) down[i] = f.getTimeMs();
                else if (!pressed && down[i] >= 0) {
                    double len = (f.getTimeMs() - down[i]) / rate;
                    if (len >= 0 && len < 2000) { lengths.add(len); perKey[i]++; }
                    down[i] = -1;
                }
            }
        }
        h.put("count", lengths.size());
        if (lengths.isEmpty()) { h.put("verdict", "few"); return h; }
        double[] v = lengths.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mean = Arrays.stream(v).average().orElse(0);
        double var = 0;
        for (double x : v) var += (x - mean) * (x - mean);
        double sd = Math.sqrt(var / v.length);
        h.put("mean", round1(mean));
        h.put("sd", round1(sd));
        h.put("median", round1(v[v.length / 2]));
        h.put("p10", round1(v[(int) (v.length * 0.1)]));
        h.put("p90", round1(v[(int) Math.min(v.length - 1, v.length * 0.9)]));
        // histogram in 5 ms steps up to 250 ms (mania holds go into the last cell)
        int[] bins = new int[51];
        for (double x : v) bins[(int) Math.min(50, x / 5)]++;
        h.put("hist", bins);
        String verdict;
        if (v.length < 100) verdict = "few";
        else if (sd < 4 || mean < 12) verdict = "suspicious";
        else verdict = "ok";
        h.put("verdict", verdict);
        return h;
    }

    private static boolean keyPressed(int k, int i, int mode) {
        if (mode == 3) return (k & (1 << i)) != 0;
        if (mode == 1) return (k & (1 << i)) != 0;
        // osu!: K1 = 4 (with M1 bit), K2 = 8 (with M2 bit), mouse buttons alone = 1 / 2
        switch (i) {
            case 0: return (k & 4) != 0;
            case 1: return (k & 8) != 0;
            case 2: return (k & 1) != 0 && (k & 4) == 0;
            default: return (k & 2) != 0 && (k & 8) == 0;
        }
    }

    // ---- snaps ----

    static Map<String, Object> snaps(List<ReplayFrame> frames, AnalysisResult a) {
        Map<String, Object> s = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        List<NoteResult> notes = a.getNoteResults();
        double radius = a.getRadius() > 0 ? a.getRadius() : 40;
        int count = 0;
        int ni = 0;
        for (int i = 1; i + 1 < frames.size(); i++) {
            ReplayFrame p0 = frames.get(i - 1), p1 = frames.get(i), p2 = frames.get(i + 1);
            if (p1.getTimeMs() - p0.getTimeMs() > 50 || p2.getTimeMs() - p1.getTimeMs() > 50) continue;
            double ax = p0.getX() - p1.getX(), ay = p0.getY() - p1.getY();
            double bx = p2.getX() - p1.getX(), by = p2.getY() - p1.getY();
            double da = Math.hypot(ax, ay), db = Math.hypot(bx, by);
            if (da < 8 || db < 8) continue;
            double cos = (ax * bx + ay * by) / (da * db);
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
            if (angle > 10) continue;
            // only next to a note around its time
            long t = p1.getTimeMs();
            while (ni < notes.size() && notes.get(ni).getNoteTimeMs() < t - 200) ni++;
            NoteResult near = null;
            for (int j = ni; j < notes.size() && notes.get(j).getNoteTimeMs() <= t + 200; j++) {
                NoteResult n = notes.get(j);
                if (Math.hypot(n.getNoteX() - p1.getX(), n.getNoteY() - p1.getY()) <= radius * 2) { near = n; break; }
            }
            if (near == null) continue;
            count++;
            if (list.size() < MAX_SNAPS) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("t", t);
                m.put("angle", round1(angle));
                m.put("out", round1(da));
                m.put("back", round1(db));
                list.add(m);
            }
        }
        s.put("count", count);
        s.put("list", list);
        s.put("verdict", count == 0 ? "ok" : count < 5 ? "info" : "suspicious");
        return s;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
