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

        double od = osu.getOd();
        double mult = (mods & ReplayAnalyzer.MOD_HARDROCK) != 0 ? 1 / 1.4 : (mods & ReplayAnalyzer.MOD_EASY) != 0 ? 1.4 : 1;
        double wMax = 16 * mult;
        double w300 = (64 - 3 * od) * mult;
        double w200 = (97 - 3 * od) * mult;
        double w100 = (127 - 3 * od) * mult;
        double w50 = (151 - 3 * od) * mult;
        double wMiss = (188 - 3 * od) * mult;
        ReplayAnalyzer.addWindow(result, "MAX", wMax / rate);
        ReplayAnalyzer.addWindow(result, "300", w300 / rate);
        ReplayAnalyzer.addWindow(result, "200", w200 / rate);
        ReplayAnalyzer.addWindow(result, "100", w100 / rate);
        ReplayAnalyzer.addWindow(result, "50", w50 / rate);

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
        for (ReplayFrame f : osr.getFrames()) {
            int mask = Math.max(0, (int) f.getX());
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

        NoteResult[] results = new NoteResult[notes.size()];
        List<Double> all = new ArrayList<>();
        List<List<Double>> colErrors = new ArrayList<>();
        List<List<Double>> colRelease = new ArrayList<>();
        int[] colMiss = new int[keys];
        for (int c = 0; c < keys; c++) { colErrors.add(new ArrayList<>()); colRelease.add(new ArrayList<>()); }

        for (int c = 0; c < keys; c++) {
            List<Integer> col = byColumn.get(c);
            int next = 0;
            for (long[] press : presses.get(c)) {
                long p = press[0];
                // notes whose window has passed are misses
                while (next < col.size() && p > notes.get(col.get(next)).getTimeMs() + w50) {
                    results[col.get(next)] = miss(notes.get(col.get(next)), c);
                    next++;
                }
                if (next >= col.size()) break;
                HitObject note = notes.get(col.get(next));
                double err = p - note.getTimeMs();
                if (err < -wMiss) continue; // too early: the press does nothing
                NoteResult nr = base(note, c);
                if (Math.abs(err) > w50) {
                    nr.setVerdict("miss");
                } else {
                    double real = err / rate;
                    nr.setClickTimeMs(p);
                    nr.setTimingError(real);
                    double a = Math.abs(err);
                    nr.setVerdict(a <= wMax ? "MAX" : a <= w300 ? "300" : a <= w200 ? "200" : a <= w100 ? "100" : "50");
                    all.add(real);
                    colErrors.get(c).add(real);
                    if (real < 0) result.setEarlyHits(result.getEarlyHits() + 1);
                    else result.setLateHits(result.getLateHits() + 1);
                    if (note.isHold() && press[1] > 0) {
                        double rel = (press[1] - note.getEndTimeMs()) / rate;
                        nr.setReleaseError(rel);
                        colRelease.get(c).add(rel);
                    }
                }
                results[col.get(next)] = nr;
                next++;
            }
            while (next < col.size()) {
                results[col.get(next)] = miss(notes.get(col.get(next)), c);
                next++;
            }
        }

        int misses = 0;
        for (int i = 0; i < notes.size(); i++) {
            NoteResult nr = results[i];
            if ("miss".equals(nr.getVerdict())) { misses++; colMiss[column[i]]++; }
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
