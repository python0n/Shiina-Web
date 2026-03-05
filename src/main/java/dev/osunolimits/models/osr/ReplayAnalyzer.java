package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import dev.osunolimits.modules.osr.OsrParser.OsrData;
import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;

import lombok.Data;

public class ReplayAnalyzer {

    @Data
    public static class AnalysisResult {
        private double avgTimingError;
        private double timingStdDev;
        private int earlyHits;
        private int lateHits;
        private int missCount;
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
        private List<NoteResult> noteResults = new ArrayList<>();
        private List<String> weaknesses = new ArrayList<>();
        private List<String> insights = new ArrayList<>();
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
        private boolean inStream;
        private boolean inJump;
        private double bpmAtNote;
    }

    private static final int MIN_STREAM_LENGTH = 4;
    private static final double JUMP_DISTANCE_THRESHOLD = 100.0;

    public static AnalysisResult analyze(OsrData osr, OsuData osu, int officialMissCount, float cs) {
        double circleRadius = 54.4 - 4.48 * cs;
        AnalysisResult result = new AnalysisResult();

        if (osr.getFrames().isEmpty() || osu.getHitObjects().isEmpty()) {
            result.getInsights().add("No data available for analysis.");
            return result;
        }

        int[] hitWindows = OsuFileParser.TimingPoint.getHitWindows(osu.getOd());
        int w300 = hitWindows[0];
        int w100 = hitWindows[1];
        int w50  = hitWindows[2];

        List<ReplayFrame> clicks = extractClicks(osr.getFrames());
        List<HitObject> circles = osu.getHitObjects().stream().filter(HitObject::isCircle).toList();

        List<Double> timingErrors = new ArrayList<>();
        List<Double> aimErrors = new ArrayList<>();
        int streamHits=0, streamTotal=0, jumpHits=0, jumpTotal=0, burstHits=0, burstTotal=0;
        double totalBpm=0, maxBpm=0; int bpmCount=0;

        for (int i = 0; i < circles.size(); i++) {
            HitObject note = circles.get(i);
            double bpm = OsuFileParser.getBpmAt(osu, note.getTimeMs());
            if (bpm > 0) { totalBpm += bpm; if (bpm > maxBpm) maxBpm = bpm; bpmCount++; }

            boolean inStream = OsuFileParser.isInStream(osu, i, MIN_STREAM_LENGTH);
            boolean inJump = isJump(osu, i);
            boolean inBurst = !inStream && !inJump && isNearby(osu, i);

            NoteResult nr = new NoteResult();
            nr.noteTimeMs = note.getTimeMs();
            nr.noteX = note.getX();
            nr.noteY = note.getY();
            nr.inStream = inStream;
            nr.inJump = inJump;
            nr.bpmAtNote = bpm;

            int bestClickIdx = findBestClickIndex(clicks, note, w50, circleRadius);
            ReplayFrame bestClick = bestClickIdx >= 0 ? clicks.remove(bestClickIdx) : null;
            if (bestClick != null) {
                double timingErr = bestClick.getTimeMs() - note.getTimeMs();
                double aimErr = distance(bestClick.getX(), bestClick.getY(), note.getX(), note.getY());
                nr.clickTimeMs = bestClick.getTimeMs();
                nr.timingError = timingErr;
                nr.aimError = aimErr;
                timingErrors.add(timingErr);
                aimErrors.add(aimErr);
                long absT = Math.abs((long) timingErr);
                if (absT <= w300) nr.verdict = "300";
                else if (absT <= w100) nr.verdict = "100";
                else if (absT <= w50) nr.verdict = "50";
                else nr.verdict = "miss";
                if (timingErr < 0) result.earlyHits++; else result.lateHits++;
                boolean hit = !nr.verdict.equals("miss");
                if (inStream) { streamTotal++; if (hit) streamHits++; }
                else if (inJump) { jumpTotal++; if (hit) jumpHits++; }
                else if (inBurst) { burstTotal++; if (hit) burstHits++; }
            } else {
                nr.verdict = "miss";
                result.missCount++;
                if (inStream) streamTotal++;
                else if (inJump) jumpTotal++;
                else if (inBurst) burstTotal++;
            }
            result.noteResults.add(nr);
        }

        if (!timingErrors.isEmpty()) {
            result.avgTimingError = timingErrors.stream().mapToDouble(d->d).average().orElse(0);
            result.timingStdDev = stdDev(timingErrors);
        }
        if (!aimErrors.isEmpty()) {
            result.avgAimError = aimErrors.stream().mapToDouble(d->d).average().orElse(0);
            result.aimStdDev = stdDev(aimErrors);
        }

        result.streamNotes = streamTotal; result.jumpNotes = jumpTotal; result.burstNotes = burstTotal;
        result.streamAccuracy = streamTotal > 0 ? (double)streamHits/streamTotal*100 : -1;
        result.jumpAccuracy   = jumpTotal   > 0 ? (double)jumpHits/jumpTotal*100     : -1;
        result.burstAccuracy  = burstTotal  > 0 ? (double)burstHits/burstTotal*100   : -1;
        result.avgBpm = bpmCount > 0 ? totalBpm/bpmCount : 0;
        result.maxBpm = maxBpm;

        result.setMissCount(officialMissCount);
        generateInsights(result);
        return result;
    }

    private static List<ReplayFrame> extractClicks(List<ReplayFrame> frames) {
        List<ReplayFrame> clicks = new ArrayList<>();
        ReplayFrame prev = null;
        for (ReplayFrame f : frames) {
            if (f.wasClickedBefore(prev)) clicks.add(f);
            prev = f;
        }
        return clicks;
    }

    private static int findBestClickIndex(List<ReplayFrame> clicks, HitObject note, int hitWindow, double circleRadius) {
        int bestIdx = -1; long bestDist = Long.MAX_VALUE;
        for (int ci = 0; ci < clicks.size(); ci++) {
            ReplayFrame c = clicks.get(ci);
            long dist = Math.abs(c.getTimeMs() - note.getTimeMs());
            double aimDist = distance(c.getX(), c.getY(), note.getX(), note.getY());
            if (dist <= hitWindow && aimDist <= circleRadius && dist < bestDist) { bestDist = dist; bestIdx = ci; }
        }
        return bestIdx;
    }

    private static boolean isJump(OsuData osu, int i) {
        List<HitObject> o = osu.getHitObjects();
        if (i <= 0 || i >= o.size()) return false;
        if (!o.get(i).isCircle() || !o.get(i-1).isCircle()) return false;
        return distance(o.get(i).getX(), o.get(i).getY(), o.get(i-1).getX(), o.get(i-1).getY()) >= JUMP_DISTANCE_THRESHOLD;
    }

    private static boolean isNearby(OsuData osu, int i) {
        List<HitObject> o = osu.getHitObjects();
        if (i <= 0 || i >= o.size()-1) return false;
        long gap = o.get(i+1).getTimeMs() - o.get(i).getTimeMs();
        return gap > 0 && gap < 300;
    }

    private static double distance(float x1, float y1, float x2, float y2) {
        double dx=x1-x2, dy=y1-y2;
        return Math.sqrt(dx*dx+dy*dy);
    }

    private static double stdDev(List<Double> v) {
        if (v.size() < 2) return 0;
        double mean = v.stream().mapToDouble(d->d).average().orElse(0);
        return Math.sqrt(v.stream().mapToDouble(d->(d-mean)*(d-mean)).average().orElse(0));
    }

    private static void generateInsights(AnalysisResult r) {
        List<String> ins = r.insights; List<String> wk = r.weaknesses;
        if (Math.abs(r.avgTimingError) > 10) {
            if (r.avgTimingError < 0) { ins.add(String.format("Your timing is on average %.0f ms early.", Math.abs(r.avgTimingError))); wk.add("early_timing"); }
            else { ins.add(String.format("Your timing is on average %.0f ms late.", r.avgTimingError)); wk.add("late_timing"); }
        } else ins.add(String.format("Timing well centered (avg %.1f ms).", r.avgTimingError));
        if (r.timingStdDev > 40) { ins.add(String.format("Unstable timing (±%.0f ms) — practice with a metronome.", r.timingStdDev)); wk.add("unstable_timing"); }
        else ins.add(String.format("Timing stability: ±%.0f ms.", r.timingStdDev));
        if (r.avgAimError > 30) { ins.add(String.format("Inaccurate aim — avg %.0f px from center.", r.avgAimError)); wk.add("poor_aim"); }
        else ins.add(String.format("Aim: avg %.0f px from center.", r.avgAimError));
        // stream/jump accuracy removed - unreliable without full hit detection
        if (r.missCount > 5) ins.add(String.format("%d misses — check your aim and timing.", r.missCount));
    }
}
