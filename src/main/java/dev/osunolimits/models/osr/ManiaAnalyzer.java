package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;
import dev.osunolimits.modules.osr.ReplayAnalyzer.NoteResult;

/**
 * Replay analysis for osu!mania maps (not converts).
 *
 * In a mania replay the x field of every frame holds the pressed columns as bits.
 * Each key press hits the earliest note in its column that has not been judged yet,
 * like in the game: a press inside the miss window but outside the 50 window is a miss,
 * a press before the miss window does nothing. Hit windows follow osu!stable
 * (OD based, HR divides them by 1.4, EZ multiplies them by 1.4). Timing errors are
 * reported in real time, like the in-game hit error meter.
 */
public final class ManiaAnalyzer {

    private ManiaAnalyzer() {}

    public static AnalysisResult analyze(OsrParser.OsrData osr, OsuData osu, int officialMissCount, int mods) {
        AnalysisResult result = new AnalysisResult();
        result.setMode(3);
        result.setMissCount(officialMissCount);
        int keys = Math.max(1, Math.min(18, Math.round(osu.getCs())));
        result.setKeyCount(keys);
        List<HitObject> notes = osu.getHitObjects();
        if (osr.getFrames().isEmpty() || notes.isEmpty()) {
            ReplayAnalyzer.insight(result, "No data available for analysis.", "no_data", "");
            return result;
        }
        double rate = ReplayAnalyzer.clockRate(mods);
        result.setClockRate(rate);
        boolean v2 = (mods & MOD_SCOREV2) != 0;

        // Hit windows in real milliseconds: in osu!mania rate mods (DT, NC, HT) do not change them.
        // HR divides them by 1.4 and EZ multiplies them by 1.4. MAX is a fixed 16 ms in stable,
        // with ScoreV2 it depends on OD.
        double od = osu.getOd();
        double mult = (mods & ReplayAnalyzer.MOD_HARDROCK) != 0 ? 1 / 1.4 : (mods & ReplayAnalyzer.MOD_EASY) != 0 ? 1.4 : 1;
        double wMax = v2 ? (od <= 5 ? 22.4 - 0.6 * od : 24.9 - 1.1 * od) * mult : 16;
        double w300 = (64 - 3 * od) * mult;
        double w200 = (97 - 3 * od) * mult;
        double w100 = (127 - 3 * od) * mult;
        double w50 = (151 - 3 * od) * mult;
        double wMiss = (188 - 3 * od) * mult;
        double[] windows = {wMax, w300, w200, w100, w50};
        String[] names = {"MAX", "300", "200", "100", "50"};
        for (int i = 0; i < windows.length; i++) ReplayAnalyzer.addWindow(result, names[i], windows[i]);

        // notes per column, in time order
        List<List<Integer>> byColumn = new ArrayList<>();
        for (int c = 0; c < keys; c++) byColumn.add(new ArrayList<>());
        int[] column = new int[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            int c = (int) Math.floor(notes.get(i).getX() * keys / 512f);
            c = Math.max(0, Math.min(keys - 1, c));
            column[i] = c;
            byColumn.get(c).add(i);
        }
        for (List<Integer> l : byColumn) l.sort((a, b) -> Long.compare(notes.get(a).getTimeMs(), notes.get(b).getTimeMs()));

        // presses and releases per column
        List<List<long[]>> presses = new ArrayList<>(); // {pressTime, releaseTime or -1}
        for (int c = 0; c < keys; c++) presses.add(new ArrayList<>());
        int prevMask = 0;
        long[][] open = new long[keys][];
        long lastFrame = 0;
        for (ReplayFrame f : osr.getFrames()) {
            int mask = Math.max(0, (int) f.getX());
            lastFrame = Math.max(lastFrame, f.getTimeMs());
            for (int c = 0; c < keys; c++) {
                boolean now = (mask & (1 << c)) != 0, before = (prevMask & (1 << c)) != 0;
                if (now && !before) {
                    open[c] = new long[]{f.getTimeMs(), -1};
                    presses.get(c).add(open[c]);
                } else if (!now && before && open[c] != null) {
                    open[c][1] = f.getTimeMs();
                    open[c] = null;
                }
            }
            prevMask = mask;
        }
        for (int c = 0; c < keys; c++) if (open[c] != null) open[c][1] = lastFrame;

        NoteResult[] results = new NoteResult[notes.size()];
        List<Double> all = new ArrayList<>();
        List<List<Double>> colErrors = new ArrayList<>();
        List<List<Double>> colRelease = new ArrayList<>();
        int[] colMiss = new int[keys];
        for (int c = 0; c < keys; c++) { colErrors.add(new ArrayList<>()); colRelease.add(new ArrayList<>()); }

        for (int c = 0; c < keys; c++) {
            List<Integer> col = byColumn.get(c);
            List<long[]> colPresses = presses.get(c);
            int next = 0;
            for (long[] press : colPresses) {
                long p = press[0];
                // a note not pressed by the end of its late 100 window is a miss (there are no late 50s)
                while (next < col.size() && (p - notes.get(col.get(next)).getTimeMs()) / rate > Math.floor(w100) + 0.5) {
                    results[col.get(next)] = unhit(notes.get(col.get(next)), c, colPresses, rate, w50, w100, v2);
                    next++;
                }
                if (next >= col.size()) break;
                HitObject note = notes.get(col.get(next));
                double real = (p - note.getTimeMs()) / rate;
                if (real < -wMiss - 0.5) continue; // too early: the press does nothing
                NoteResult nr = base(note, c);
                long a = Math.round(Math.abs(real));
                if (real < 0 && a > Math.floor(w50)) {
                    nr.setVerdict("miss"); // early, inside the miss window but outside 50
                } else {
                    nr.setClickTimeMs(p);
                    nr.setTimingError(real);
                    nr.setVerdict(judge(a, windows, names));
                    all.add(real);
                    colErrors.get(c).add(real);
                    if (real < 0) result.setEarlyHits(result.getEarlyHits() + 1);
                    else result.setLateHits(result.getLateHits() + 1);
                    if (note.isHold()) {
                        double tail = (press[1] - note.getEndTimeMs()) / rate;
                        nr.setReleaseError(tail);
                        colRelease.get(c).add(tail);
                        judgeHold(nr, Math.abs(real), tail, windows, v2);
                    }
                }
                results[col.get(next)] = nr;
                next++;
            }
            while (next < col.size()) {
                results[col.get(next)] = unhit(notes.get(col.get(next)), c, colPresses, rate, w50, w100, v2);
                next++;
            }
        }

        int misses = 0;
        for (int i = 0; i < notes.size(); i++) {
            NoteResult nr = results[i];
            if ("miss".equals(nr.getVerdict())) { misses++; colMiss[column[i]]++; }
            if ("miss".equals(nr.getTailVerdict())) misses++;
            result.getNoteResults().add(nr);
        }
        result.setAnalyzerMisses(misses);
        result.setAnalyzedObjects(notes.size());
        if (!all.isEmpty()) {
            result.setAvgTimingError(ReplayAnalyzer.mean(all));
            result.setTimingStdDev(ReplayAnalyzer.stdDev(all));
            result.setUnstableRate(result.getTimingStdDev() * 10);
        }
        for (int c = 0; c < keys; c++) {
            int objects = byColumn.get(c).size();
            if (objects == 0) continue;
            var m = ReplayAnalyzer.groupStat(String.valueOf(c + 1), objects, objects - colMiss[c], colErrors.get(c));
            m.put("release", colRelease.get(c).isEmpty() ? "-" : ReplayAnalyzer.fmt("%+.1f", ReplayAnalyzer.mean(colRelease.get(c))));
            result.getGroupStats().add(m);
        }
        ReplayAnalyzer.timingInsights(result, 250);
        columnInsights(result, colErrors);
        return result;
    }

    private static void columnInsights(AnalysisResult r, List<List<Double>> colErrors) {
        // the column whose average is furthest from the rest
        double overall = r.getAvgTimingError();
        int worst = -1;
        double worstDiff = 0;
        for (int c = 0; c < colErrors.size(); c++) {
            if (colErrors.get(c).size() < 20) continue;
            double d = ReplayAnalyzer.mean(colErrors.get(c)) - overall;
            if (Math.abs(d) > Math.abs(worstDiff)) { worstDiff = d; worst = c; }
        }
        if (worst >= 0 && Math.abs(worstDiff) >= 5) {
            String key = worstDiff < 0 ? "column_early" : "column_late";
            String ms = ReplayAnalyzer.fmt("%.0f", Math.abs(worstDiff));
            ReplayAnalyzer.insight(r, "Column " + (worst + 1) + " is hit " + ms
                + " ms " + (worstDiff < 0 ? "earlier" : "later") + " than the others.", key, String.valueOf(worst + 1), ms);
        }
    }

    private static final int MOD_SCOREV2 = 1 << 29;

    private static String judge(long absError, double[] windows, String[] names) {
        for (int i = 0; i < windows.length; i++) if (absError <= Math.floor(windows[i])) return names[i];
        return "miss";
    }

    /**
     * Hold note judgement. Stable (ScoreV1): one judgement from the press and the release together,
     * the combined error being head error + release error. ScoreV2: the head is judged like a note
     * and the release on its own, with windows 1.5x longer. Letting go during the body allows 50 at most.
     */
    private static void judgeHold(NoteResult nr, double head, double tail, double[] w, boolean v2) {
        double wMax = w[0], w300 = w[1], w200 = w[2], w100 = w[3], w50 = w[4];
        if (v2) {
            double t = Math.abs(tail);
            String tv;
            if (tail > w100 * 1.5) tv = "miss";                 // held too long: no late 50
            else if (tail < -w50 * 1.5) tv = "50";              // let go during the body
            else tv = t <= wMax * 1.5 ? "MAX" : t <= w300 * 1.5 ? "300" : t <= w200 * 1.5 ? "200" : t <= w100 * 1.5 ? "100" : "50";
            nr.setTailVerdict(tv);
            return;
        }
        if (tail < -w50) { nr.setVerdict("50"); return; }    // let go during the body
        double comb = head + Math.min(Math.abs(tail), w100 * 2);
        if (head <= wMax * 1.2 && comb <= wMax * 2.4) nr.setVerdict("MAX");
        else if (head <= w300 * 1.1 && comb <= w300 * 2.2) nr.setVerdict("300");
        else if (head <= w200 && comb <= w200 * 2) nr.setVerdict("200");
        else if (head <= w100 && comb <= w100 * 2) nr.setVerdict("100");
        else nr.setVerdict("50");
    }

    /** A note nobody pressed in time. For a hold note held through its end, stable still gives 50. */
    private static NoteResult unhit(HitObject note, int column, List<long[]> presses, double rate, double w50, double w100, boolean v2) {
        NoteResult nr = miss(note, column);
        if (note.isHold()) {
            long from = note.getEndTimeMs() - Math.round(w50 * rate), to = note.getEndTimeMs() + Math.round(w100 * rate);
            boolean heldAtEnd = false;
            for (long[] p : presses) {
                if (p[0] <= to && p[1] >= from && p[0] >= note.getTimeMs()) { heldAtEnd = true; break; }
            }
            if (v2) nr.setTailVerdict(heldAtEnd ? "50" : "miss");
            else if (heldAtEnd) nr.setVerdict("50");
        }
        return nr;
    }

    private static NoteResult base(HitObject note, int column) {
        NoteResult nr = new NoteResult();
        nr.setNoteTimeMs(note.getTimeMs());
        nr.setColumn(column);
        nr.setSlider(note.isHold());
        return nr;
    }

    private static NoteResult miss(HitObject note, int column) {
        NoteResult nr = base(note, column);
        nr.setVerdict("miss");
        return nr;
    }
}
