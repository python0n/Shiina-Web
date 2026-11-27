package dev.osunolimits.modules.osr;

import java.util.List;

import dev.osunolimits.modules.osr.OsuFileParser.HitObject;

/**
 * Stacking of osu!standard objects, as the game does it: objects that lie on top of each
 * other within a short time get a stack height, and each level moves the object a little
 * up and to the left. Ported from osu! (OsuBeatmapProcessor): the current algorithm for
 * beatmap format v6 and newer, the old one for older files.
 */
public final class Stacking {

    private static final double STACK_DISTANCE = 3;

    private Stacking() {}

    /** Time before the hit when the object appears, from AR (after mods). */
    public static double preempt(double ar) {
        return ar > 5 ? 1200 - 750 * (ar - 5) / 5 : 1200 + 600 * (5 - ar) / 5;
    }

    /** Object scale from CS (after mods); the stack offset per level is scale * 6.4 px. */
    public static double scale(double cs) {
        return (1.0f - 0.7f * (cs - 5) / 5) / 2;
    }

    public static void apply(List<HitObject> objects, int formatVersion, double stackLeniency, double ar) {
        for (HitObject o : objects) o.setStackHeight(0);
        double threshold = preempt(ar) * stackLeniency;
        if (formatVersion >= 6) applyCurrent(objects, threshold);
        else applyOld(objects, threshold);
    }

    private static double dist(float x1, float y1, float x2, float y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void applyCurrent(List<HitObject> h, double stackThreshold) {
        int count = h.size();
        int startIndex = 0;
        for (int i = count - 1; i > startIndex; i--) {
            int n = i;
            HitObject objectI = h.get(i);
            if (objectI.getStackHeight() != 0 || objectI.isSpinner()) continue;

            if (objectI.isCircle()) {
                while (--n >= 0) {
                    HitObject objectN = h.get(n);
                    if (objectN.isSpinner()) continue;
                    if (objectI.getTimeMs() - objectN.getEndTimeMs() > stackThreshold) break;

                    if (objectN.isSlider() && dist(objectN.getEndX(), objectN.getEndY(), objectI.getX(), objectI.getY()) < STACK_DISTANCE) {
                        int offset = objectI.getStackHeight() - objectN.getStackHeight() + 1;
                        for (int j = n + 1; j <= i; j++) {
                            HitObject objectJ = h.get(j);
                            if (dist(objectN.getEndX(), objectN.getEndY(), objectJ.getX(), objectJ.getY()) < STACK_DISTANCE) {
                                objectJ.setStackHeight(objectJ.getStackHeight() - offset);
                            }
                        }
                        break;
                    }

                    if (dist(objectN.getX(), objectN.getY(), objectI.getX(), objectI.getY()) < STACK_DISTANCE) {
                        objectN.setStackHeight(objectI.getStackHeight() + 1);
                        objectI = objectN;
                    }
                }
            } else if (objectI.isSlider()) {
                while (--n >= startIndex) {
                    HitObject objectN = h.get(n);
                    if (objectN.isSpinner()) continue;
                    if (objectI.getTimeMs() - objectN.getTimeMs() > stackThreshold) break;
                    if (dist(objectN.getEndX(), objectN.getEndY(), objectI.getX(), objectI.getY()) < STACK_DISTANCE) {
                        objectN.setStackHeight(objectI.getStackHeight() + 1);
                        objectI = objectN;
                    }
                }
            }
        }
    }

    private static void applyOld(List<HitObject> h, double stackThreshold) {
        for (int i = 0; i < h.size(); i++) {
            HitObject curr = h.get(i);
            if (curr.getStackHeight() != 0 && !curr.isSlider()) continue;
            double startTime = curr.getEndTimeMs();
            int sliderStack = 0;
            // the old algorithm compares with the end of the path, without repeats
            float[] tail = curr.isSlider() && curr.getPath() != null ? curr.getPath().positionAt(1) : new float[]{curr.getX(), curr.getY()};
            for (int j = i + 1; j < h.size(); j++) {
                HitObject oj = h.get(j);
                if (oj.getTimeMs() - stackThreshold > startTime) break;
                if (dist(oj.getX(), oj.getY(), curr.getX(), curr.getY()) < STACK_DISTANCE) {
                    curr.setStackHeight(curr.getStackHeight() + 1);
                    startTime = oj.getEndTimeMs();
                } else if (dist(oj.getX(), oj.getY(), tail[0], tail[1]) < STACK_DISTANCE) {
                    sliderStack++;
                    oj.setStackHeight(oj.getStackHeight() - sliderStack);
                    startTime = oj.getEndTimeMs();
                }
            }
        }
    }
}
