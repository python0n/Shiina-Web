package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;
import dev.osunolimits.modules.osr.ReplayAnalyzer.NoteResult;

/**
 * Replay analysis for osu!taiko maps (not converts).
 *
 * Keys in a taiko replay: 1 = left centre, 4 = right centre (don), 2 = left rim, 8 = right rim (kat).
 * Each press hits the earliest note that has not been judged yet; a press of the wrong colour
 * inside the window is a miss, like in the game. Drumrolls and swells are skipped.
 * Hit windows follow osu!stable: great 50 - 3*OD, good and miss depend on OD.
 */
public final class TaikoAnalyzer {

    private static final int DON = 1 | 4;
    private static final int KAT = 2 | 8;
    /** a second press this close to a big note belongs to the same note */
    private static final int BIG_SECOND_HIT_MS = 30;

    private TaikoAnalyzer() {}

    public static AnalysisResult analyze(OsrParser.OsrData osr, OsuData osu, int officialMissCount, int mods) {
        AnalysisResult result = new AnalysisResult();
        result.setMode(1);
        result.setMissCount(officialMissCount);
        List<HitObject> notes = new ArrayList<>();
        for (HitObject o : osu.getHitObjects()) if (o.isCircle()) notes.add(o);
        if (osr.getFrames().isEmpty() || notes.isEmpty()) {
            ReplayAnalyzer.insight(result, "No data available for analysis.", "no_data", "");
            return result;
        }
        double rate = ReplayAnalyzer.clockRate(mods);
        result.setClockRate(rate);
        double od = ReplayAnalyzer.applyMods(osu.getOd(), mods, 1.4f);
        double great = 50 - 3 * od;
        double good = od <= 5 ? 120 - 8 * od : 110 - 6 * od;
        double missW = od <= 5 ? 135 - 8 * od : 120 - 5 * od;
        ReplayAnalyzer.addWindow(result, "GREAT", great / rate);
        ReplayAnalyzer.addWindow(result, "GOOD", good / rate);

        // presses: {time, colour} where colour 0 = don, 1 = kat
        List<long[]> presses = new ArrayList<>();
        int prev = 0;
        for (ReplayFrame f : osr.getFrames()) {
            int k = f.getKeys() & 0x0F;
            int rising = k & ~prev;
            if ((rising & DON) != 0) presses.add(new long[]{f.getTimeMs(), 0});
            if ((rising & KAT) != 0) presses.add(new long[]{f.getTimeMs(), 1});
            prev = k;
        }

        NoteResult[] results = new NoteResult[notes.size()];
        List<Double> all = new ArrayList<>();
        List<List<Double>> byColour = List.of(new ArrayList<>(), new ArrayList<>());
        int[] colourObjects = new int[2], colourMiss = new int[2];
        int next = 0;
        long lastBigHit = Long.MIN_VALUE;
        int lastBigColour = -1;
        for (long[] press : presses) {
            long p = press[0];
            if (lastBigColour == press[1] && p - lastBigHit <= BIG_SECOND_HIT_MS) continue;
            while (next < notes.size() && p > notes.get(next).getTimeMs() + good) {
                results[next] = miss(notes.get(next));
                next++;
            }
            if (next >= notes.size()) break;
            HitObject note = notes.get(next);
            double err = p - note.getTimeMs();
            if (err < -missW) continue;
            NoteResult nr = base(note);
            int colour = nr.isKat() ? 1 : 0;
            if (Math.abs(err) > good) {
                nr.setVerdict("miss");
            } else if (colour != press[1]) {
                nr.setVerdict("wrong");
            } else {
                double real = err / rate;
                nr.setClickTimeMs(p);
                nr.setTimingError(real);
                nr.setVerdict(Math.abs(err) <= great ? "GREAT" : "GOOD");
                all.add(real);
                byColour.get(colour).add(real);
                if (real < 0) result.setEarlyHits(result.getEarlyHits() + 1);
                else result.setLateHits(result.getLateHits() + 1);
                if (nr.isBig()) { lastBigHit = p; lastBigColour = colour; }
            }
            results[next] = nr;
            next++;
        }
        while (next < notes.size()) { results[next] = miss(notes.get(next)); next++; }

        int misses = 0;
        for (NoteResult nr : results) {
            int colour = nr.isKat() ? 1 : 0;
            colourObjects[colour]++;
            if ("miss".equals(nr.getVerdict()) || "wrong".equals(nr.getVerdict())) { misses++; colourMiss[colour]++; }
            result.getNoteResults().add(nr);
        }
        result.setAnalyzerMisses(misses);
        result.setAnalyzedObjects(notes.size());
        if (!all.isEmpty()) {
            result.setAvgTimingError(ReplayAnalyzer.mean(all));
            result.setTimingStdDev(ReplayAnalyzer.stdDev(all));
            result.setUnstableRate(result.getTimingStdDev() * 10);
        }
        String[] names = {"don", "kat"};
        for (int c = 0; c < 2; c++) {
            if (colourObjects[c] == 0) continue;
            result.getGroupStats().add(ReplayAnalyzer.groupStat(names[c], colourObjects[c], colourObjects[c] - colourMiss[c], byColour.get(c)));
        }
        ReplayAnalyzer.timingInsights(result, 200);
        if (byColour.get(0).size() >= 20 && byColour.get(1).size() >= 20) {
            double d = ReplayAnalyzer.mean(byColour.get(1)) - ReplayAnalyzer.mean(byColour.get(0));
            if (Math.abs(d) >= 5) {
                String ms = ReplayAnalyzer.fmt("%.0f", Math.abs(d));
                ReplayAnalyzer.insight(result, "Kat notes are hit " + ms + " ms " + (d < 0 ? "earlier" : "later") + " than don notes.",
                    d < 0 ? "kat_early" : "kat_late", ms);
            }
        }
        return result;
    }

    private static NoteResult base(HitObject note) {
        NoteResult nr = new NoteResult();
        nr.setNoteTimeMs(note.getTimeMs());
        nr.setKat((note.getHitSound() & (2 | 8)) != 0);
        nr.setBig((note.getHitSound() & 4) != 0);
        return nr;
    }

    private static NoteResult miss(HitObject note) {
        NoteResult nr = base(note);
        nr.setVerdict("miss");
        return nr;
    }
}
