package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;

import lombok.Data;

/**
 * Replay analysis for osu!standard.
 *
 * Clicks are matched to objects the way the stable client does it (notelock: a click can only
 * hit the earliest object that hasn't been hit or missed yet). Objects are placed where the game
 * shows them: stacked objects are moved by their stack height, and HR flips the playfield.
 * Mods are taken into account: HR raises CS/OD/AR, EZ lowers them, DT/NC/HT change the clock rate.
 * Timing errors are reported in real time (milliseconds as the player felt them), so the
 * numbers match the in-game hit error meter and unstable rate.
 * A slider head that was not clicked is not a miss in the game (the slider can still be
 * finished), so it is reported separately.
 */
public class ReplayAnalyzer {

    @Data
    public static class AnalysisResult {
        private int mode;
        private double avgTimingError;
        private double timingStdDev;
        private double unstableRate;
        private int earlyHits;
        private int lateHits;
        private int missCount;          // misses from the game (the score)
        private int analyzerMisses;     // objects the analyzer found no hit for
        private int sliderHeadMisses;   // std: slider heads without a click (not misses in the game)
        private int analyzedObjects;
        private double avgAimError;
        private double aimStdDev;
        private double radius;          // std: circle radius in osu! pixels, after mods
        private double streamAccuracy;
        private double jumpAccuracy;
        private double burstAccuracy;
        private int streamNotes;
        private int jumpNotes;
        private int burstNotes;
        private double avgBpm;
        private double maxBpm;
        private double clockRate = 1.0;
        private boolean relax;          // std with Relax: timing means when the cursor reached the note
        private int keyCount;           // mania
        // hit windows in real milliseconds (after mods and rate), the widest last
        private List<Map<String, Object>> hitWindows = new ArrayList<>();
        // per pattern (std), per colour (taiko) or per column (mania)
        private List<Map<String, Object>> groupStats = new ArrayList<>();
        private List<NoteResult> noteResults = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> insights = new ArrayList<>();
        // same insights as language-neutral codes, translated by the template: analyzer.ins.KEY with {v}
        private List<Map<String, String>> insightCodes = new ArrayList<>();
        private List<Map<String, Object>> recommendedMaps = new ArrayList<>();
    }

    @Data
    public static class NoteResult {
        private long noteTimeMs;
        private float noteX;
        private float noteY;
        private long clickTimeMs;
        private double timingError;
        private double aimError;
        private double aimAlong;    // std: + = past the note in the direction of movement, - = short of it
        private double aimAcross;   // std: + = to the right of the movement, - = to the left
        private boolean aimDirected;
        private String verdict;
        private boolean slider;
        private boolean inStream;
        private boolean inJump;
        private boolean inBurst;
        private double bpmAtNote;
        private int column = -1;    // mania
        private boolean kat;        // taiko
        private boolean big;        // taiko
        private Double releaseError; // mania hold notes
        private String tailVerdict;  // mania hold notes with ScoreV2 (judged apart from the head)
        private String sliderResult; // std sliders: 300/100/50/miss from the head, ticks, repeats and end
    }

    static final int MOD_EASY = 2;
    static final int MOD_HARDROCK = 16;
    static final int MOD_DOUBLETIME = 64;
    static final int MOD_HALFTIME = 256;
    static final int MOD_NIGHTCORE = 512;
    static final int MOD_RELAX = 128;
    private static final int RELAX_LENIENCY = 3;

    private static final int MIN_STREAM_LENGTH = 4;
    private static final double JUMP_DISTANCE_THRESHOLD = 100.0;
    private static final float PLAYFIELD_HEIGHT = 384f;

    public static double clockRate(int mods) {
        if ((mods & (MOD_DOUBLETIME | MOD_NIGHTCORE)) != 0) return 1.5;
        if ((mods & MOD_HALFTIME) != 0) return 0.75;
        return 1.0;
    }

    static float applyMods(float value, int mods, float hrMultiplier) {
        if ((mods & MOD_HARDROCK) != 0) return Math.min(value * hrMultiplier, 10f);
        if ((mods & MOD_EASY) != 0) return value * 0.5f;
        return value;
    }

    /** Stacks the objects and returns {x, y, endX, endY} of every object as shown in the game (HR flip included). */
    public static float[][] placedPositions(OsuData osu, int mods) {
        boolean hardRock = (mods & MOD_HARDROCK) != 0;
        float cs = applyMods(osu.getCs(), mods, 1.3f);
        float ar = applyMods(osu.getAr(), mods, 1.4f);
        List<HitObject> all = osu.getHitObjects();
        Stacking.apply(all, osu.getFormatVersion(), osu.getStackLeniency(), ar);
        double offset = Stacking.scale(cs) * -6.4;
        float[][] pos = new float[all.size()][];
        for (int i = 0; i < all.size(); i++) {
            HitObject o = all.get(i);
            float y = hardRock ? PLAYFIELD_HEIGHT - o.getY() : o.getY();
            float endY = hardRock ? PLAYFIELD_HEIGHT - o.getEndY() : o.getEndY();
            float s = (float) (o.getStackHeight() * offset);
            pos[i] = new float[]{o.getX() + s, y + s, o.getEndX() + s, endY + s};
        }
        return pos;
    }

    public static AnalysisResult analyze(OsrData osr, OsuData osu, int officialMissCount, int mods) {
        AnalysisResult result = new AnalysisResult();
        result.mode = 0;
        result.setMissCount(officialMissCount);
        if (osr.getFrames().isEmpty() || osu.getHitObjects().isEmpty()) {
            insight(result, "No data available for analysis.", "no_data", "");
            return result;
        }

        double rate = clockRate(mods);
        result.clockRate = rate;
        float cs = applyMods(osu.getCs(), mods, 1.3f);
        float od = applyMods(osu.getOd(), mods, 1.4f);
        double radius = 54.4 - 4.48 * cs;
        result.radius = radius;
        // hit windows in song time (the replay is in song time too)
        int[] hitWindows = OsuFileParser.TimingPoint.getHitWindows(od);
        int w300 = hitWindows[0], w100 = hitWindows[1], w50 = hitWindows[2];
        addWindow(result, "300", w300 / rate);
        addWindow(result, "100", w100 / rate);
        addWindow(result, "50", w50 / rate);

        List<HitObject> all = osu.getHitObjects();
        float[][] pos = placedPositions(osu, mods);
        boolean hardRock = (mods & MOD_HARDROCK) != 0;
        List<ReplayFrame> frames = new ArrayList<>(osr.getFrames());
        frames.sort((a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));

        // clickable objects: circles and slider heads; spinners don't need aim or timing
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (!all.get(i).isSpinner()) idx.add(i);
        }

        int n = idx.size();
        ReplayFrame[] hitBy = new ReplayFrame[n];
        boolean relax = (mods & MOD_RELAX) != 0;
        result.relax = relax;
        // with Relax the replay has no key presses: the game clicks for the player
        List<ReplayFrame> clicks = relax ? List.of() : extractClicks(osr.getFrames());
        if (relax) relaxHits(osr.getFrames(), all, idx, pos, radius, w50, hitBy);

        // notelock matching
        int next = 0;
        for (ReplayFrame click : clicks) {
            while (next < n && click.getTimeMs() > all.get(idx.get(next)).getTimeMs() + w50) next++;
            if (next >= n) break;
            HitObject o = all.get(idx.get(next));
            if (click.getTimeMs() < o.getTimeMs() - w50) continue;
            float[] p = pos[idx.get(next)];
            if (distance(click.getX(), click.getY(), p[0], p[1]) <= radius) {
                hitBy[next] = click;
                next++;
            }
        }

        List<Double> timingErrors = new ArrayList<>();
        List<Double> aimErrors = new ArrayList<>();
        // per pattern: stream, jump, burst, other
        String[] groups = {"stream", "jump", "burst", "other"};
        Map<String, List<Double>> groupErrors = new LinkedHashMap<>();
        Map<String, int[]> groupCounts = new LinkedHashMap<>(); // {objects, hits}
        for (String g : groups) { groupErrors.put(g, new ArrayList<>()); groupCounts.put(g, new int[2]); }
        int streamHits = 0, streamTotal = 0, jumpHits = 0, jumpTotal = 0, burstHits = 0, burstTotal = 0;
        int allHits = 0;
        double totalBpm = 0, maxBpm = 0;
        int bpmCount = 0;

        for (int k = 0; k < n; k++) {
            int i = idx.get(k);
            HitObject note = all.get(i);
            float[] p = pos[i];
            double bpm = OsuFileParser.getBpmAt(osu, note.getTimeMs()) * rate;
            if (bpm > 0) { totalBpm += bpm; maxBpm = Math.max(maxBpm, bpm); bpmCount++; }

            boolean inStream = OsuFileParser.isInStream(osu, i, MIN_STREAM_LENGTH);
            boolean inJump = isJump(osu, i);
            boolean inBurst = !inStream && !inJump && isNearby(osu, i);
            String group = inStream ? "stream" : inJump ? "jump" : inBurst ? "burst" : "other";

            NoteResult nr = new NoteResult();
            nr.noteTimeMs = note.getTimeMs();
            nr.noteX = p[0];
            nr.noteY = p[1];
            nr.slider = note.isSlider();
            nr.inStream = inStream;
            nr.inJump = inJump;
            nr.inBurst = inBurst;
            nr.bpmAtNote = bpm;

            ReplayFrame click = hitBy[k];
            boolean hit = click != null;
            groupCounts.get(group)[0]++;
            if (hit) {
                double songErr = click.getTimeMs() - note.getTimeMs();
                double realErr = songErr / rate;
                double aimErr = distance(click.getX(), click.getY(), p[0], p[1]);
                nr.clickTimeMs = click.getTimeMs();
                nr.timingError = realErr;
                nr.aimError = aimErr;
                directAim(nr, click, p, previousEnd(all, pos, idx, k));
                timingErrors.add(realErr);
                aimErrors.add(aimErr);
                groupErrors.get(group).add(realErr);
                groupCounts.get(group)[1]++;
                long absSong = Math.abs(Math.round(songErr));
                if (note.isSlider()) {
                    nr.verdict = "slider";
                    nr.sliderResult = sliderResult(osu, note, p, true, frames, radius, relax, hardRock);
                }
                // stable: abs(round(error)) < floor(window)
                else if (absSong < w300) nr.verdict = "300";
                else if (absSong < w100) nr.verdict = "100";
                else nr.verdict = "50";
                if (realErr < 0) result.earlyHits++; else result.lateHits++;
                allHits++;
            } else if (note.isSlider()) {
                nr.sliderResult = sliderResult(osu, note, p, false, frames, radius, relax, hardRock);
                if ("miss".equals(nr.sliderResult)) {
                    // nothing of the slider was held: a real miss in the game
                    nr.verdict = "miss";
                    result.analyzerMisses++;
                } else {
                    nr.verdict = "slider_miss";
                    result.sliderHeadMisses++;
                }
            } else {
                nr.verdict = "miss";
                result.analyzerMisses++;
            }
            if (inStream) { streamTotal++; if (hit) streamHits++; }
            else if (inJump) { jumpTotal++; if (hit) jumpHits++; }
            else if (inBurst) { burstTotal++; if (hit) burstHits++; }
            result.noteResults.add(nr);
        }

        result.analyzedObjects = n;
        if (!timingErrors.isEmpty()) {
            result.avgTimingError = mean(timingErrors);
            result.timingStdDev = stdDev(timingErrors);
            result.unstableRate = result.timingStdDev * 10;
        }
        if (!aimErrors.isEmpty()) {
            result.avgAimError = mean(aimErrors);
            result.aimStdDev = stdDev(aimErrors);
        }
        for (String g : groups) {
            int[] c = groupCounts.get(g);
            if (c[0] == 0) continue;
            result.groupStats.add(groupStat(g, c[0], c[1], groupErrors.get(g)));
        }
        result.streamNotes = streamTotal;
        result.jumpNotes = jumpTotal;
        result.burstNotes = burstTotal;
        result.streamAccuracy = streamTotal > 0 ? (double) streamHits / streamTotal * 100 : -1;
        result.jumpAccuracy = jumpTotal > 0 ? (double) jumpHits / jumpTotal * 100 : -1;
        result.burstAccuracy = burstTotal > 0 ? (double) burstHits / burstTotal * 100 : -1;
        result.avgBpm = bpmCount > 0 ? totalBpm / bpmCount : 0;
        result.maxBpm = maxBpm;

        double overall = n > 0 ? (double) allHits / n * 100 : 100;
        generateInsights(result, overall);
        return result;
    }

    /**
     * Stable judges a slider by how many of its parts were held: the head, every tick, every
     * repeat and the end (checked a little before the real end, like lazer's legacy last tick).
     * All of them = 300, at least half = 100, at least one = 50, none = miss. The ball is
     * followed while a key is held and the cursor is within the circle radius, and once
     * following, within 2.4 times the radius.
     */
    static String sliderResult(OsuData osu, HitObject o, float[] p, boolean headHit,
                               List<ReplayFrame> frames, double radius, boolean relax, boolean hardRock) {
        if (o.getPath() == null) return headHit ? "300" : "miss";
        List<double[]> events = CatchBeatmap.sliderEvents(osu, o);
        double start = o.getTimeMs(), end = o.getEndTimeMs();
        int slides = Math.max(1, o.getSlides());
        double span = (end - start) / slides;
        double shift = p[0] - o.getX(); // stack offset
        int total = 1, held = headHit ? 1 : 0;

        // first frame at or after a bit before the slider
        int f = 0, lo = 0, hi = frames.size() - 1;
        while (lo <= hi) { int mid = (lo + hi) >>> 1; if (frames.get(mid).getTimeMs() < start - 1) lo = mid + 1; else hi = mid - 1; }
        f = Math.max(0, lo - 1);
        boolean tracking = false;
        for (double[] e : events) {
            int type = (int) e[2];
            if (type != 1 && type != 2 && type != 3) continue;
            total++;
            double t = e[0];
            while (f < frames.size() && frames.get(f).getTimeMs() <= t) {
                ReplayFrame fr = frames.get(f);
                boolean pressed = relax || (fr.getKeys() & 0x0F) != 0;
                double[] b = ball(o, fr.getTimeMs(), start, span, slides, shift, hardRock);
                double d = distance(fr.getX(), fr.getY(), (float) b[0], (float) b[1]);
                tracking = pressed && d <= (tracking ? radius * 2.4 : radius);
                f++;
            }
            if (tracking) held++;
        }
        if (held == total) return "300";
        if (held * 2 >= total) return "100";
        if (held > 0) return "50";
        return "miss";
    }

    /** Slider ball position at time t, placed like the notes (stack offset, HR). */
    private static double[] ball(HitObject o, double t, double start, double span, int slides, double shift, boolean hardRock) {
        double k = span > 0 ? Math.max(0, Math.min(slides, (t - start) / span)) : 0;
        int si = (int) Math.min(slides - 1, Math.floor(k));
        double frac = k - si;
        if (si % 2 == 1) frac = 1 - frac;
        float[] q = o.getPath().positionAt(frac);
        double y = hardRock ? PLAYFIELD_HEIGHT - q[1] : q[1];
        return new double[]{q[0] + shift, y + shift};
    }

    /** Where the cursor came from: the end of the previous clickable object. */
    private static float[] previousEnd(List<HitObject> all, float[][] pos, List<Integer> idx, int k) {
        if (k == 0) return null;
        float[] p = pos[idx.get(k - 1)];
        return new float[]{p[2], p[3]};
    }

    /** Splits the aim error into "along the movement" and "across it". */
    private static void directAim(NoteResult nr, ReplayFrame click, float[] note, float[] from) {
        double ex = click.getX() - note[0], ey = click.getY() - note[1];
        if (from == null) return;
        double mx = note[0] - from[0], my = note[1] - from[1];
        double len = Math.sqrt(mx * mx + my * my);
        if (len < 20) return; // no clear direction (stacks, short movements)
        mx /= len; my /= len;
        nr.aimAlong = ex * mx + ey * my;
        // screen y grows downwards, so (mx, my) turned 90 degrees clockwise points to the right
        nr.aimAcross = ex * -my + ey * mx;
        nr.aimDirected = true;
    }

    static void addWindow(AnalysisResult r, String name, double ms) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("name", name);
        w.put("ms", Math.round(ms * 10) / 10.0);
        r.hitWindows.add(w);
    }

    static Map<String, Object> groupStat(String key, int objects, int hits, List<Double> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("objects", objects);
        m.put("hits", hits);
        m.put("hitRate", String.format(Locale.ROOT, "%.1f", objects > 0 ? hits * 100.0 / objects : 0));
        m.put("avg", errors.isEmpty() ? "-" : String.format(Locale.ROOT, "%+.1f", mean(errors)));
        m.put("ur", errors.size() < 2 ? "-" : String.format(Locale.ROOT, "%.0f", stdDev(errors) * 10));
        return m;
    }

    /**
     * Relax: the game presses for the player as soon as the cursor is over the object, from 3 ms
     * before its time until its 50 window ends (like osu!'s Relax mod). One click per object,
     * at the first replay frame that meets this.
     */
    private static void relaxHits(List<ReplayFrame> frames, List<HitObject> all, List<Integer> idx,
                                  float[][] pos, double radius, int w50, ReplayFrame[] hitBy) {
        List<ReplayFrame> sorted = new ArrayList<>(frames);
        sorted.sort((a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));
        int f = 0;
        for (int n = 0; n < idx.size(); n++) {
            int i = idx.get(n);
            HitObject o = all.get(i);
            double from = o.getTimeMs() - RELAX_LENIENCY, to = o.getTimeMs() + w50;
            while (f + 1 < sorted.size() && sorted.get(f + 1).getTimeMs() <= from) f++;
            // the cursor moves between frames, so check the path between them, not only the frames
            for (int k = f; k + 1 < sorted.size() && sorted.get(k).getTimeMs() <= to; k++) {
                ReplayFrame a = sorted.get(k), b = sorted.get(k + 1);
                double t = firstInside(a, b, pos[i][0], pos[i][1], radius, from, to);
                if (!Double.isNaN(t)) {
                    double u = b.getTimeMs() > a.getTimeMs() ? (t - a.getTimeMs()) / (b.getTimeMs() - a.getTimeMs()) : 0;
                    hitBy[n] = virtualFrame(Math.round(t), a.getX() + (b.getX() - a.getX()) * u, a.getY() + (b.getY() - a.getY()) * u);
                    break;
                }
            }
        }
    }

    /** Earliest time in [from, to] at which the cursor, moving straight from a to b, is inside the circle. */
    private static double firstInside(ReplayFrame a, ReplayFrame b, float cx, float cy, double r, double from, double to) {
        double t0 = a.getTimeMs(), t1 = b.getTimeMs();
        double lo = Math.max(t0, from), hi = Math.min(t1, to);
        if (hi < lo) return Double.NaN;
        double dt = t1 - t0;
        // position at time t: p(t) = a + (b - a) * (t - t0) / dt
        double ax = a.getX() - cx, ay = a.getY() - cy;
        double vx = dt > 0 ? (b.getX() - a.getX()) / dt : 0, vy = dt > 0 ? (b.getY() - a.getY()) / dt : 0;
        // |(ax + vx s) , (ay + vy s)|^2 <= r^2 with s = t - t0
        double A = vx * vx + vy * vy, B = 2 * (ax * vx + ay * vy), C = ax * ax + ay * ay - r * r;
        double sLo = lo - t0, sHi = hi - t0;
        if (A * sLo * sLo + B * sLo + C <= 0) return lo;
        if (A < 1e-12) return Double.NaN;
        double disc = B * B - 4 * A * C;
        if (disc < 0) return Double.NaN;
        double s1 = (-B - Math.sqrt(disc)) / (2 * A);
        return s1 >= sLo && s1 <= sHi ? t0 + s1 : Double.NaN;
    }

    private static ReplayFrame virtualFrame(long time, double x, double y) {
        ReplayFrame f = new ReplayFrame();
        f.setTimeMs(time);
        f.setX((float) x);
        f.setY((float) y);
        return f;
    }

    static List<ReplayFrame> extractClicks(List<ReplayFrame> frames) {
        List<ReplayFrame> clicks = new ArrayList<>();
        ReplayFrame prev = null;
        for (ReplayFrame f : frames) {
            if (f.wasClickedBefore(prev)) clicks.add(f);
            prev = f;
        }
        clicks.sort((a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));
        return clicks;
    }

    private static boolean isJump(OsuData osu, int i) {
        List<HitObject> o = osu.getHitObjects();
        if (i <= 0 || i >= o.size()) return false;
        if (o.get(i).isSpinner() || o.get(i - 1).isSpinner()) return false;
        return distance(o.get(i).getX(), o.get(i).getY(), o.get(i - 1).getX(), o.get(i - 1).getY()) >= JUMP_DISTANCE_THRESHOLD;
    }

    private static boolean isNearby(OsuData osu, int i) {
        List<HitObject> o = osu.getHitObjects();
        if (i <= 0 || i >= o.size() - 1) return false;
        long gap = o.get(i + 1).getTimeMs() - o.get(i).getTimeMs();
        return gap > 0 && gap < 300;
    }

    static double distance(float x1, float y1, float x2, float y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    static double mean(List<Double> v) {
        return v.stream().mapToDouble(d -> d).average().orElse(0);
    }

    static double stdDev(List<Double> v) {
        if (v.size() < 2) return 0;
        double mean = mean(v);
        return Math.sqrt(v.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0));
    }

    static void insight(AnalysisResult r, String english, String key, String value) {
        insight(r, english, key, value, "");
    }

    /** value goes into {v} of the translation, value2 into {w}. */
    static void insight(AnalysisResult r, String english, String key, String value, String value2) {
        r.insights.add(english);
        Map<String, String> code = new HashMap<>();
        code.put("key", key);
        code.put("value", value);
        code.put("value2", value2);
        r.insightCodes.add(code);
    }

    static String fmt(String pattern, double v) {
        return String.format(Locale.ROOT, pattern, v);
    }

    /** Timing insights shared by every mode. */
    static void timingInsights(AnalysisResult r, double unstableLimit) {
        List<String> wk = r.weaknesses;
        if (Math.abs(r.avgTimingError) > 10) {
            String v = fmt("%.0f", Math.abs(r.avgTimingError));
            if (r.avgTimingError < 0) { insight(r, "Your timing is on average " + v + " ms early.", "early", v); wk.add("early_timing"); }
            else { insight(r, "Your timing is on average " + v + " ms late.", "late", v); wk.add("late_timing"); }
        } else {
            insight(r, "Timing well centered (avg " + fmt("%.1f", r.avgTimingError) + " ms).", "centered", fmt("%.1f", r.avgTimingError));
        }
        String ur = fmt("%.0f", r.unstableRate);
        if (r.unstableRate > unstableLimit) { insight(r, "Unstable timing (UR " + ur + ") - practice with a metronome.", "unstable_ur", ur); wk.add("unstable_timing"); }
        else insight(r, "Timing stability: UR " + ur + ".", "stability_ur", ur);
    }

    private static void generateInsights(AnalysisResult r, double overallHitRate) {
        List<String> wk = r.weaknesses;
        if (r.relax) insight(r, "Relax: the game clicks for you, so timing shows when your cursor reached each note.", "relax", "");
        timingInsights(r, 200);
        String aim = fmt("%.0f", r.avgAimError);
        if (r.avgAimError > 30) { insight(r, "Inaccurate aim - avg " + aim + " px from center.", "poor_aim", aim); wk.add("poor_aim"); }
        else insight(r, "Aim: avg " + aim + " px from center.", "aim", aim);
        // overshoot / undershoot: only with enough directed hits
        List<Double> along = new ArrayList<>();
        for (NoteResult n : r.noteResults) if (n.aimDirected) along.add(n.aimAlong);
        if (along.size() >= 30) {
            double a = mean(along);
            if (a > 4) insight(r, "You tend to overshoot notes (avg " + fmt("%.0f", a) + " px past them).", "overshoot", fmt("%.0f", a));
            else if (a < -4) insight(r, "You tend to stop short of notes (avg " + fmt("%.0f", -a) + " px before them).", "undershoot", fmt("%.0f", -a));
        }
        // skill types: only when there are enough notes of that type to say something
        if (r.streamNotes >= 30 && r.streamAccuracy >= 0 && r.streamAccuracy < overallHitRate - 5) {
            insight(r, "Streams: you hit " + fmt("%.0f", r.streamAccuracy) + "% of stream notes, clearly less than the rest of the map.", "weak_streams", fmt("%.0f", r.streamAccuracy));
            wk.add("weak_streams");
        }
        if (r.jumpNotes >= 30 && r.jumpAccuracy >= 0 && r.jumpAccuracy < overallHitRate - 5) {
            insight(r, "Jumps: you hit " + fmt("%.0f", r.jumpAccuracy) + "% of jumps, clearly less than the rest of the map.", "weak_jumps", fmt("%.0f", r.jumpAccuracy));
            wk.add("weak_jumps");
        }
        if (r.missCount > 5) insight(r, r.missCount + " misses - check your aim and timing.", "misses", String.valueOf(r.missCount));
    }
}
