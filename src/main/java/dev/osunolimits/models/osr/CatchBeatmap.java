package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.modules.osr.OsuFileParser.HitObject;
import dev.osunolimits.modules.osr.OsuFileParser.OsuData;
import dev.osunolimits.modules.osr.OsuFileParser.TimingPoint;

/**
 * osu!catch objects as the game builds them from a beatmap (osu!catch maps and osu! converts):
 * circles become fruits, sliders become juice streams (fruits at the head, repeats and tail,
 * droplets on the ticks, tiny droplets between them), spinners become banana showers.
 * Then the stable position offsets are applied with the same random generator (seed 1337),
 * including the Hard Rock offsets, and hyperdashes are marked.
 * Ported from osu! (CatchBeatmapProcessor, JuiceStream, BananaShower, SliderEventGenerator).
 */
public final class CatchBeatmap {

    public static final float WIDTH = 512;
    private static final int RNG_SEED = 1337;
    private static final double BASE_DASH_SPEED = 1.0;
    private static final float ALLOWED_CATCH_RANGE = 0.8f;
    private static final float BASE_CATCHER_SIZE = 106.75f;
    private static final int TAIL_LENIENCY = -36;

    public enum Kind { FRUIT, DROPLET, TINY, BANANA }

    public static final class CatchObject {
        public final Kind kind;
        public final double time;
        public final float originalX;
        public float xOffset;
        public boolean hyperDash;
        /** index of the beatmap object this comes from */
        public final int source;

        CatchObject(Kind kind, double time, float x, int source) {
            this.kind = kind;
            this.time = time;
            this.originalX = x;
            this.source = source;
        }

        public float x() {
            return Math.max(0, Math.min(WIDTH, originalX + xOffset));
        }
    }

    private CatchBeatmap() {}

    /** Width of the catcher in which a fruit is caught, after mods (CS). */
    public static double catchWidth(double cs) {
        double scale = 1.0f - 0.7f * (cs - 5) / 5;
        return BASE_CATCHER_SIZE * Math.abs(scale) * ALLOWED_CATCH_RANGE;
    }

    public static List<CatchObject> build(OsuData osu, int mods) {
        boolean hardRock = (mods & ReplayAnalyzer.MOD_HARDROCK) != 0;
        float cs = ReplayAnalyzer.applyMods(osu.getCs(), mods, 1.3f);
        List<CatchObject> out = new ArrayList<>();
        // objects per beatmap object, in order, so the random offsets are drawn in the game's order
        List<List<CatchObject>> groups = new ArrayList<>();
        List<HitObject> all = osu.getHitObjects();
        for (int i = 0; i < all.size(); i++) {
            HitObject h = all.get(i);
            List<CatchObject> g = new ArrayList<>();
            if (h.isSpinner()) {
                bananas(h, i, g);
            } else if (h.isSlider() && h.getPath() != null) {
                juiceStream(osu, h, i, g);
            } else {
                g.add(new CatchObject(Kind.FRUIT, h.getTimeMs(), h.getX(), i));
            }
            groups.add(g);
        }
        applyOffsets(all, groups, hardRock);
        for (List<CatchObject> g : groups) out.addAll(g);
        out.sort((a, b) -> Double.compare(a.time, b.time));
        hyperDash(out, catchWidth(cs) / 2 / ALLOWED_CATCH_RANGE);
        return out;
    }

    // ---- objects ----

    private static void bananas(HitObject h, int index, List<CatchObject> g) {
        double duration = h.getEndTimeMs() - h.getTimeMs();
        double spacing = duration;
        while (spacing > 100) spacing /= 2;
        if (spacing <= 0) return;
        for (double t = h.getTimeMs(); t <= h.getEndTimeMs(); t += spacing) {
            g.add(new CatchObject(Kind.BANANA, t, 0, index));
        }
    }

    /** {beat length, slider velocity multiplier, raw inherited beat length} at a time. */
    private static double[] timing(OsuData osu, double time) {
        double beatLength = 500, sv = 1, svBeatLength = -100;
        boolean found = false;
        for (TimingPoint tp : osu.getTimingPoints()) {
            if (tp.getTime() > time) break;
            if (!tp.isInherited()) {
                beatLength = tp.getBeatLength();
                sv = 1;
                svBeatLength = -100;
                found = true;
            } else if (tp.getBeatLength() < 0) {
                svBeatLength = tp.getBeatLength();
                sv = -100.0 / svBeatLength;
            }
        }
        if (!found) {
            for (TimingPoint tp : osu.getTimingPoints()) {
                if (!tp.isInherited()) { beatLength = tp.getBeatLength(); break; }
            }
        }
        return new double[]{beatLength, sv, svBeatLength};
    }

    private static void juiceStream(OsuData osu, HitObject h, int index, List<CatchObject> g) {
        SliderPath path = h.getPath();
        List<double[]> events = sliderEvents(osu, h);
        juiceFromEvents(path, events, index, g);
    }

    /**
     * Slider events like lazer's SliderEventGenerator: {time, path progress, type} with
     * type 0 head, 1 tick, 2 repeat, 3 legacy last tick, 4 tail. Shared by catch and osu!.
     */
    static List<double[]> sliderEvents(OsuData osu, HitObject h) {
        double[] tm = timing(osu, h.getTimeMs());
        // the game's beat length adjusted by the slider velocity, as catch and taiko compute it
        double bpmMultiplier = tm[2] < 0 ? Math.max(10, Math.min(10000, (float) -tm[2])) / 100.0 : 1;
        double velocity = 100 * osu.getSliderMultiplier() / (tm[0] * bpmMultiplier);
        double scoringDistance = velocity * tm[0];
        double tickDistanceMultiplier = osu.getFormatVersion() < 8 ? 1.0 / tm[1] : 1;
        double tickDistance = scoringDistance / osu.getSliderTickRate() * tickDistanceMultiplier;

        SliderPath path = h.getPath();
        double length = Math.min(100000, path.getLength());
        int spans = Math.max(1, h.getSlides());
        double spanDuration = velocity > 0 ? length / velocity : 0;
        double start = h.getTimeMs();

        // events: {time, path progress, type}  type 0 head, 1 tick, 2 repeat, 3 legacy last tick, 4 tail
        List<double[]> events = new ArrayList<>();
        events.add(new double[]{start, 0, 0});
        tickDistance = Math.max(0, Math.min(length, tickDistance));
        double minDistanceFromEnd = velocity * 10;
        for (int span = 0; span < spans; span++) {
            double spanStart = start + span * spanDuration;
            boolean reversed = span % 2 == 1;
            List<double[]> ticks = new ArrayList<>();
            if (tickDistance != 0) {
                for (double d = tickDistance; d <= length; d += tickDistance) {
                    if (d >= length - minDistanceFromEnd) break;
                    double progress = d / length;
                    double timeProgress = reversed ? 1 - progress : progress;
                    ticks.add(new double[]{spanStart + timeProgress * spanDuration, progress, 1});
                }
            }
            if (reversed) java.util.Collections.reverse(ticks);
            events.addAll(ticks);
            if (span < spans - 1) events.add(new double[]{spanStart + spanDuration, (span + 1) % 2, 2});
        }
        double total = spans * spanDuration;
        double finalSpanStart = start + (spans - 1) * spanDuration;
        double lastTickTime = Math.max(start + total / 2, finalSpanStart + spanDuration + TAIL_LENIENCY);
        double lastTickProgress = spanDuration > 0 ? (lastTickTime - finalSpanStart) / spanDuration : 0;
        if (spans % 2 == 0) lastTickProgress = 1 - lastTickProgress;
        events.add(new double[]{lastTickTime, lastTickProgress, 3});
        events.add(new double[]{start + total, spans % 2, 4});
        return events;
    }

    private static void juiceFromEvents(SliderPath path, List<double[]> events, int index, List<CatchObject> g) {
        double[] last = null;
        for (double[] e : events) {
            if (last != null) {
                double sinceLast = (int) e[0] - (int) last[0];
                if (sinceLast > 80) {
                    double between = sinceLast;
                    while (between > 100) between /= 2;
                    for (double t = between; t < sinceLast; t += between) {
                        double p = last[1] + (t / sinceLast) * (e[1] - last[1]);
                        g.add(new CatchObject(Kind.TINY, t + last[0], path.positionAt(p)[0], index));
                    }
                }
            }
            last = e;
            int type = (int) e[2];
            if (type == 1) g.add(new CatchObject(Kind.DROPLET, e[0], path.positionAt(e[1])[0], index));
            else if (type != 3) g.add(new CatchObject(Kind.FRUIT, e[0], path.positionAt(e[1])[0], index));
        }
    }

    // ---- stable position offsets ----

    private static void applyOffsets(List<HitObject> all, List<List<CatchObject>> groups, boolean hardRock) {
        LegacyRandom rng = new LegacyRandom(RNG_SEED);
        Float lastPosition = null;
        double lastStartTime = 0;
        for (int i = 0; i < all.size(); i++) {
            HitObject h = all.get(i);
            List<CatchObject> g = groups.get(i);
            if (h.isSpinner()) {
                for (CatchObject b : g) {
                    b.xOffset = (float) (rng.nextDouble() * WIDTH);
                    rng.next(); // stable: banana type
                    rng.next(); // stable: banana rotation
                    rng.next(); // stable: banana colour
                }
            } else if (h.isSlider() && h.getPath() != null) {
                // stable used the last control point and the start time here (both bugs, kept on purpose)
                lastPosition = lastControlPointX(h);
                lastStartTime = h.getTimeMs();
                for (CatchObject o : g) {
                    o.xOffset = 0;
                    if (o.kind == Kind.TINY) {
                        o.xOffset = Math.max(-o.originalX, Math.min(WIDTH - o.originalX, rng.next(-20, 20)));
                    } else if (o.kind == Kind.DROPLET) {
                        rng.next(); // stable: droplet rotation
                    }
                }
            } else if (!g.isEmpty() && hardRock) {
                CatchObject f = g.get(0);
                float offsetPosition = f.originalX;
                double startTime = f.time;
                if (lastPosition == null || lastPosition == 0) {
                    lastPosition = offsetPosition;
                    lastStartTime = startTime;
                    continue;
                }
                float positionDiff = offsetPosition - lastPosition;
                int timeDiff = (int) (startTime - lastStartTime);
                if (timeDiff > 1000) {
                    lastPosition = offsetPosition;
                    lastStartTime = startTime;
                    continue;
                }
                if (positionDiff == 0) {
                    boolean right = rng.nextBool();
                    float rand = Math.min(20, (float) rng.next(0, Math.max(0, timeDiff / 4d)));
                    if (right) offsetPosition = offsetPosition + rand <= WIDTH ? offsetPosition + rand : offsetPosition - rand;
                    else offsetPosition = offsetPosition - rand >= 0 ? offsetPosition - rand : offsetPosition + rand;
                    f.xOffset = offsetPosition - f.originalX;
                    continue;
                }
                if (Math.abs(positionDiff) < timeDiff / 3) {
                    if (positionDiff > 0) { if (offsetPosition + positionDiff < WIDTH) offsetPosition += positionDiff; }
                    else if (offsetPosition + positionDiff > 0) offsetPosition += positionDiff;
                }
                f.xOffset = offsetPosition - f.originalX;
                lastPosition = offsetPosition;
                lastStartTime = startTime;
            }
        }
    }

    private static float lastControlPointX(HitObject h) {
        String[] parts = h.getCurve().split("\\|");
        for (int i = parts.length - 1; i >= 1; i--) {
            String[] xy = parts[i].split(":");
            try {
                return Float.parseFloat(xy[0]);
            } catch (NumberFormatException ignored) {}
        }
        return h.getX();
    }

    // ---- hyperdashes ----

    private static void hyperDash(List<CatchObject> objects, double halfCatcherWidth) {
        List<CatchObject> palpable = new ArrayList<>();
        for (CatchObject o : objects) if (o.kind == Kind.FRUIT || o.kind == Kind.DROPLET) palpable.add(o);
        int lastDirection = 0;
        double lastExcess = halfCatcherWidth;
        for (int i = 0; i < palpable.size() - 1; i++) {
            CatchObject cur = palpable.get(i), next = palpable.get(i + 1);
            cur.hyperDash = false;
            int direction = next.x() > cur.x() ? 1 : -1;
            double timeToNext = (int) next.time - (int) cur.time - 1000f / 60f / 4;
            double distanceToNext = Math.abs(next.x() - cur.x()) - (lastDirection == direction ? lastExcess : halfCatcherWidth);
            float distanceToHyper = (float) (timeToNext * BASE_DASH_SPEED - distanceToNext);
            if (distanceToHyper < 0) {
                cur.hyperDash = true;
                lastExcess = halfCatcherWidth;
            } else {
                lastExcess = Math.max(0, Math.min(halfCatcherWidth, distanceToHyper));
            }
            lastDirection = direction;
        }
    }

    /** osu!stable's random generator (xorshift), so the offsets match the game. */
    static final class LegacyRandom {
        private static final double INT_TO_REAL = 1.0 / (Integer.MAX_VALUE + 1.0);
        private int x, y = 842502087, z = (int) 3579807591L, w = 273326509;
        private int bitBuffer;
        private int bitIndex = 32;

        LegacyRandom(int seed) {
            x = seed;
        }

        int nextUInt() {
            int t = x ^ (x << 11);
            x = y;
            y = z;
            z = w;
            return w = w ^ (w >>> 19) ^ t ^ (t >>> 8);
        }

        int next() {
            return 0x7FFFFFFF & nextUInt();
        }

        double nextDouble() {
            return INT_TO_REAL * next();
        }

        int next(int lower, int upper) {
            return (int) (lower + nextDouble() * (upper - lower));
        }

        int next(double lower, double upper) {
            return (int) (lower + nextDouble() * (upper - lower));
        }

        boolean nextBool() {
            if (bitIndex == 32) {
                bitIndex = 0;
                bitBuffer = nextUInt();
            }
            return ((bitBuffer >>> bitIndex++) & 1) == 1;
        }
    }
}
