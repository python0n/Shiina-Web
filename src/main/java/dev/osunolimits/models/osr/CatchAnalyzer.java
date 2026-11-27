package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.osunolimits.modules.osr.CatchBeatmap.CatchObject;
import dev.osunolimits.modules.osr.CatchBeatmap.Kind;
import dev.osunolimits.modules.osr.OsrParser.ReplayFrame;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.ReplayAnalyzer.AnalysisResult;
import dev.osunolimits.modules.osr.ReplayAnalyzer.NoteResult;

/**
 * Replay analysis for osu!catch (catch maps and osu! converts).
 *
 * There is no timing in catch: an object is caught when the catcher is under it at the moment
 * it lands. The replay holds the catcher position (x) for every frame, so for every fruit, droplet
 * and tiny droplet the analyzer takes the catcher position at that time and checks the distance.
 * The "timing error" of a note is here its offset from the catcher centre in osu! pixels
 * (+ = the object landed right of the centre). Bananas are bonus and are not counted.
 */
public final class CatchAnalyzer {

    private CatchAnalyzer() {}

    /** Catch objects for the analyzer and the player, after mods. */
    public static List<CatchObject> objects(OsuData osu, int mods) {
        return CatchBeatmap.build(osu, mods);
    }

    public static AnalysisResult analyze(OsrParser.OsrData osr, OsuData osu, int officialMissCount, int mods) {
        AnalysisResult result = new AnalysisResult();
        result.setMode(2);
        result.setMissCount(officialMissCount);
        List<CatchObject> objects = objects(osu, mods);
        List<ReplayFrame> frames = new ArrayList<>(osr.getFrames());
        frames.sort((a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));
        if (frames.isEmpty() || objects.isEmpty()) {
            ReplayAnalyzer.insight(result, "No data available for analysis.", "no_data", "");
            return result;
        }
        result.setClockRate(ReplayAnalyzer.clockRate(mods));
        float cs = ReplayAnalyzer.applyMods(osu.getCs(), mods, 1.3f);
        double half = CatchBeatmap.catchWidth(cs) / 2;
        result.setRadius(half);
        ReplayAnalyzer.addWindow(result, "catcher", half);

        String[] names = {"fruit", "droplet", "tiny", "hyper"};
        int[][] counts = new int[4][2]; // {objects, caught}
        List<List<Double>> offsets = new ArrayList<>();
        for (int i = 0; i < 4; i++) offsets.add(new ArrayList<>());
        List<Double> all = new ArrayList<>();
        int edge = 0, caughtMain = 0, hyperMissed = 0, misses = 0;

        int f = 0;
        for (CatchObject o : objects) {
            if (o.kind == Kind.BANANA) continue;
            while (f + 1 < frames.size() && frames.get(f + 1).getTimeMs() <= o.time) f++;
            double catcher = catcherAt(frames, f, o.time);
            double offset = o.x() - catcher;
            boolean caught = Math.abs(offset) <= half;
            int kind = o.kind == Kind.FRUIT ? 0 : o.kind == Kind.DROPLET ? 1 : 2;

            NoteResult nr = new NoteResult();
            nr.setNoteTimeMs(Math.round(o.time));
            nr.setNoteX(o.x());
            nr.setColumn(kind);
            nr.setBig(o.hyperDash);
            nr.setTimingError(offset);
            nr.setClickTimeMs(caught ? Math.round(o.time) : 0);
            if (caught) {
                nr.setVerdict(names[kind]);
                all.add(offset);
                offsets.get(kind).add(offset);
                if (kind != 2) {
                    caughtMain++;
                    if (Math.abs(offset) > half * 0.8) edge++;
                }
                if (offset < 0) result.setEarlyHits(result.getEarlyHits() + 1); // left of the centre
                else result.setLateHits(result.getLateHits() + 1);           // right of the centre
            } else {
                nr.setVerdict(kind == 2 ? "tiny_miss" : "miss");
                if (kind != 2) {
                    misses++;
                    if (o.hyperDash) hyperMissed++;
                }
            }
            counts[kind][0]++;
            if (caught) counts[kind][1]++;
            if (o.hyperDash && kind != 2) {
                counts[3][0]++;
                if (caught) { counts[3][1]++; offsets.get(3).add(offset); }
            }
            result.getNoteResults().add(nr);
        }

        result.setAnalyzerMisses(misses);
        result.setAnalyzedObjects(result.getNoteResults().size());
        if (!all.isEmpty()) {
            result.setAvgTimingError(ReplayAnalyzer.mean(all));
            result.setTimingStdDev(ReplayAnalyzer.stdDev(all));
        }
        for (int i = 0; i < 4; i++) {
            if (counts[i][0] == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", names[i]);
            m.put("objects", counts[i][0]);
            m.put("hits", counts[i][1]);
            m.put("hitRate", ReplayAnalyzer.fmt("%.1f", counts[i][1] * 100.0 / counts[i][0]));
            m.put("avg", offsets.get(i).isEmpty() ? "-" : ReplayAnalyzer.fmt("%+.1f", ReplayAnalyzer.mean(offsets.get(i))));
            m.put("ur", offsets.get(i).size() < 2 ? "-" : ReplayAnalyzer.fmt("%.1f", ReplayAnalyzer.stdDev(offsets.get(i))));
            result.getGroupStats().add(m);
        }
        int mainTotal = counts[0][0] + counts[1][0];
        double caughtRate = mainTotal > 0 ? caughtMain * 100.0 / mainTotal : 100;
        double edgeRate = caughtMain > 0 ? edge * 100.0 / caughtMain : 0;
        result.setStreamAccuracy(caughtRate);   // shown as "caught" on the page
        result.setJumpAccuracy(edgeRate);       // shown as "edge catches" on the page

        insights(result, misses, hyperMissed, edgeRate, counts);
        return result;
    }

    /** Catcher x at a time, moving straight between the replay frames. */
    private static double catcherAt(List<ReplayFrame> frames, int i, double t) {
        ReplayFrame a = frames.get(i);
        if (i + 1 >= frames.size() || t <= a.getTimeMs()) return a.getX();
        ReplayFrame b = frames.get(i + 1);
        double span = b.getTimeMs() - a.getTimeMs();
        if (span <= 0) return b.getX();
        return a.getX() + (b.getX() - a.getX()) * (t - a.getTimeMs()) / span;
    }

    private static void insights(AnalysisResult r, int misses, int hyperMissed, double edgeRate, int[][] counts) {
        if (misses == 0) {
            ReplayAnalyzer.insight(r, "Every fruit and droplet was caught.", "catch_fc", "");
        } else {
            if (hyperMissed > 0)
                ReplayAnalyzer.insight(r, misses + " fruits and droplets missed, " + hyperMissed + " of them before a hyperdash.",
                    "catch_misses", String.valueOf(misses), String.valueOf(hyperMissed));
            else
                ReplayAnalyzer.insight(r, misses + " fruits and droplets missed.", "catch_misses_plain", String.valueOf(misses));
        }
        String e = ReplayAnalyzer.fmt("%.0f", edgeRate);
        if (edgeRate >= 15) ReplayAnalyzer.insight(r, e + "% of the catches were close to the edge of the catcher - a little early movement makes them safer.", "catch_edge_many", e);
        else ReplayAnalyzer.insight(r, e + "% of the catches were close to the edge of the catcher.", "catch_edge", e);
        if (counts[2][0] >= 20) {
            String t = ReplayAnalyzer.fmt("%.1f", counts[2][1] * 100.0 / counts[2][0]);
            ReplayAnalyzer.insight(r, "Tiny droplets caught: " + t + "%.", "catch_tiny", t);
        }
        // side the catches lean to
        if (Math.abs(r.getAvgTimingError()) >= 6) {
            String v = ReplayAnalyzer.fmt("%.0f", Math.abs(r.getAvgTimingError()));
            ReplayAnalyzer.insight(r, "Objects land on average " + v + " px " + (r.getAvgTimingError() < 0 ? "left" : "right") + " of the catcher centre.",
                r.getAvgTimingError() < 0 ? "catch_left" : "catch_right", v);
        }
    }
}
