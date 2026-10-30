package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;

import lombok.Data;

/**
 * Replay analysis for osu!standard.
 *
 * Clicks are matched to objects the way the stable client does it (notelock: a click can only
 * hit the earliest object that hasn't been hit or missed yet). Mods are taken into account:
 * HR flips the playfield and raises CS/OD, EZ lowers them, DT/NC/HT change the clock rate.
 * Timing errors are reported in real time (milliseconds as the player felt them), so the
 * numbers match the in-game hit error meter and unstable rate.
 * Known simplification: stacked objects use their raw positions from the .osu file.
 */
public class ReplayAnalyzer {

    @Data
    public static class AnalysisResult {
        private double avgTimingError;
        private double timingStdDev;
        private double unstableRate;
        private int earlyHits;
        private int lateHits;
        private int missCount;
        private int analyzedObjects;
        private double avgAimError;
        private double aimStdDev;
        private double streamAccuracy;
        private double jumpAccuracy;
        private double burstAccuracy;
        private int streamNotes;
        private int jumpNotes;
        private int burstNotes;
        private double avgBpm;
        private double maxBpm;
        private double clockRate = 1.0;
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
        private String verdict;
        private boolean slider;
        private boolean inStream;
        private boolean inJump;
        private double bpmAtNote;
    }

    private static final int MOD_EASY = 2;
    private static final int MOD_HARDROCK = 16;
    private static final int MOD_DOUBLETIME = 64;
    private static final int MOD_HALFTIME = 256;
    private static final int MOD_NIGHTCORE = 512;

    private static final int MIN_STREAM_LENGTH = 4;
    private static final double JUMP_DISTANCE_THRESHOLD = 100.0;
    private static final float PLAYFIELD_HEIGHT = 384f;

    public static double clockRate(int mods) {
        if ((mods & (MOD_DOUBLETIME | MOD_NIGHTCORE)) != 0) return 1.5;
        if ((mods & MOD_HALFTIME) != 0) return 0.75;
        return 1.0;
    }

    private static float applyMods(float value, int mods, float hrMultiplier) {
        if ((mods & MOD_HARDROCK) != 0) return Math.min(value * hrMultiplier, 10f);
        if ((mods & MOD_EASY) != 0) return value * 0.5f;
        return value;
    }

    public static AnalysisResult analyze(OsrData osr, OsuData osu, int officialMissCount, int mods) {
        AnalysisResult result = new AnalysisResult();
        if (osr.getFrames().isEmpty() || osu.getHitObjects().isEmpty()) {
            insight(result, "No data available for analysis.", "no_data", "");
            return result;
        }

        boolean hardRock = (mods & MOD_HARDROCK) != 0;
        double rate = clockRate(mods);
        result.clockRate = rate;
        float cs = applyMods(osu.getCs(), mods, 1.3f);
        float od = applyMods(osu.getOd(), mods, 1.4f);
        double radius = 54.4 - 4.48 * cs;
        // hit windows in song time (the replay is in song time too)
        int[] hitWindows = OsuFileParser.TimingPoint.getHitWindows(od);
        int w300 = hitWindows[0], w100 = hitWindows[1], w50 = hitWindows[2];

        // clickable objects: circles and slider heads; spinners don't need aim or timing
        List<HitObject> all = osu.getHitObjects();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (!all.get(i).isSpinner()) idx.add(i);
        }

        List<ReplayFrame> clicks = extractClicks(osr.getFrames());
        int n = idx.size();
        ReplayFrame[] hitBy = new ReplayFrame[n];

        // notelock matching
        int next = 0;
        for (ReplayFrame click : clicks) {
            while (next < n && click.getTimeMs() > all.get(idx.get(next)).getTimeMs() + w50) next++;
            if (next >= n) break;
            HitObject o = all.get(idx.get(next));
            if (click.getTimeMs() < o.getTimeMs() - w50) continue;
            float oy = hardRock ? PLAYFIELD_HEIGHT - o.getY() : o.getY();
            if (distance(click.getX(), click.getY(), o.getX(), oy) <= radius) {
                hitBy[next] = click;
                next++;
            }
        }

        List<Double> timingErrors = new ArrayList<>();
        List<Double> aimErrors = new ArrayList<>();
        int streamHits = 0, streamTotal = 0, jumpHits = 0, jumpTotal = 0, burstHits = 0, burstTotal = 0;
        int allHits = 0;
        double totalBpm = 0, maxBpm = 0;
        int bpmCount = 0;

        for (int k = 0; k < n; k++) {
            int i = idx.get(k);
            HitObject note = all.get(i);
            float noteY = hardRock ? PLAYFIELD_HEIGHT - note.getY() : note.getY();
            double bpm = OsuFileParser.getBpmAt(osu, note.getTimeMs()) * rate;
            if (bpm > 0) { totalBpm += bpm; maxBpm = Math.max(maxBpm, bpm); bpmCount++; }

            boolean inStream = OsuFileParser.isInStream(osu, i, MIN_STREAM_LENGTH);
            boolean inJump = isJump(osu, i);
            boolean inBurst = !inStream && !inJump && isNearby(osu, i);

            NoteResult nr = new NoteResult();
            nr.noteTimeMs = note.getTimeMs();
            nr.noteX = note.getX();
            nr.noteY = noteY;
            nr.slider = note.isSlider();
            nr.inStream = inStream;
            nr.inJump = inJump;
            nr.bpmAtNote = bpm;

            ReplayFrame click = hitBy[k];
            boolean hit = click != null;
            if (hit) {
                double songErr = click.getTimeMs() - note.getTimeMs();
                double realErr = songErr / rate;
                double aimErr = distance(click.getX(), click.getY(), note.getX(), noteY);
                nr.clickTimeMs = click.getTimeMs();
                nr.timingError = realErr;
                nr.aimError = aimErr;
                timingErrors.add(realErr);
                aimErrors.add(aimErr);
                long absSong = Math.abs(Math.round(songErr));
                if (note.isSlider()) nr.verdict = "slider";
                else if (absSong <= w300) nr.verdict = "300";
                else if (absSong <= w100) nr.verdict = "100";
                else nr.verdict = "50";
                if (realErr < 0) result.earlyHits++; else result.lateHits++;
                allHits++;
            } else {
                nr.verdict = "miss";
            }
            if (inStream) { streamTotal++; if (hit) streamHits++; }
            else if (inJump) { jumpTotal++; if (hit) jumpHits++; }
            else if (inBurst) { burstTotal++; if (hit) burstHits++; }
            result.noteResults.add(nr);
        }

        result.analyzedObjects = n;
        if (!timingErrors.isEmpty()) {
            result.avgTimingError = timingErrors.stream().mapToDouble(d -> d).average().orElse(0);
            result.timingStdDev = stdDev(timingErrors);
            result.unstableRate = result.timingStdDev * 10;
        }
        if (!aimErrors.isEmpty()) {
            result.avgAimError = aimErrors.stream().mapToDouble(d -> d).average().orElse(0);
            result.aimStdDev = stdDev(aimErrors);
        }
        result.streamNotes = streamTotal;
        result.jumpNotes = jumpTotal;
        result.burstNotes = burstTotal;
        result.streamAccuracy = streamTotal > 0 ? (double) streamHits / streamTotal * 100 : -1;
        result.jumpAccuracy = jumpTotal > 0 ? (double) jumpHits / jumpTotal * 100 : -1;
        result.burstAccuracy = burstTotal > 0 ? (double) burstHits / burstTotal * 100 : -1;
        result.avgBpm = bpmCount > 0 ? totalBpm / bpmCount : 0;
        result.maxBpm = maxBpm;
        result.setMissCount(officialMissCount);

        double overall = n > 0 ? (double) allHits / n * 100 : 100;
        generateInsights(result, overall);
        return result;
    }

    private static List<ReplayFrame> extractClicks(List<ReplayFrame> frames) {
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

    private static double distance(float x1, float y1, float x2, float y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double stdDev(List<Double> v) {
        if (v.size() < 2) return 0;
        double mean = v.stream().mapToDouble(d -> d).average().orElse(0);
        return Math.sqrt(v.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0));
    }

    private static void insight(AnalysisResult r, String english, String key, String value) {
        r.insights.add(english);
        Map<String, String> code = new HashMap<>();
        code.put("key", key);
        code.put("value", value);
        r.insightCodes.add(code);
    }

    private static String fmt(String pattern, double v) {
        return String.format(Locale.ROOT, pattern, v);
    }

    private static void generateInsights(AnalysisResult r, double overallHitRate) {
        List<String> wk = r.weaknesses;
        if (Math.abs(r.avgTimingError) > 10) {
            String v = fmt("%.0f", Math.abs(r.avgTimingError));
            if (r.avgTimingError < 0) { insight(r, "Your timing is on average " + v + " ms early.", "early", v); wk.add("early_timing"); }
            else { insight(r, "Your timing is on average " + v + " ms late.", "late", v); wk.add("late_timing"); }
        } else {
            insight(r, "Timing well centered (avg " + fmt("%.1f", r.avgTimingError) + " ms).", "centered", fmt("%.1f", r.avgTimingError));
        }
        String ur = fmt("%.0f", r.unstableRate);
        if (r.unstableRate > 200) { insight(r, "Unstable timing (UR " + ur + ") - practice with a metronome.", "unstable_ur", ur); wk.add("unstable_timing"); }
        else insight(r, "Timing stability: UR " + ur + ".", "stability_ur", ur);
        String aim = fmt("%.0f", r.avgAimError);
        if (r.avgAimError > 30) { insight(r, "Inaccurate aim - avg " + aim + " px from center.", "poor_aim", aim); wk.add("poor_aim"); }
        else insight(r, "Aim: avg " + aim + " px from center.", "aim", aim);
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
