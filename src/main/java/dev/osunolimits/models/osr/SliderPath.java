package dev.osunolimits.modules.osr;

import java.util.ArrayList;
import java.util.List;

/**
 * Slider path from the .osu control points, turned into a list of points and cut
 * (or extended) to the length written in the file, the way the game does it.
 * Curve types: L (linear), P (perfect circle, three points), B (bezier, a repeated
 * point starts a new segment) and C (catmull).
 */
public final class SliderPath {

    private final List<float[]> points = new ArrayList<>();
    private final List<Double> cumulative = new ArrayList<>();
    private final double length;

    private SliderPath(List<float[]> raw, double expectedLength) {
        // drop points that repeat the previous one (they add nothing to the shape)
        for (float[] p : raw) {
            if (points.isEmpty() || dist(points.get(points.size() - 1), p) > 1e-4) points.add(p);
        }
        if (points.isEmpty()) points.add(new float[]{0, 0});
        cumulative.add(0.0);
        for (int i = 1; i < points.size(); i++) {
            cumulative.add(cumulative.get(i - 1) + dist(points.get(i - 1), points.get(i)));
        }
        double calculated = cumulative.get(cumulative.size() - 1);
        if (expectedLength > 0 && points.size() >= 2 && calculated != expectedLength) {
            if (calculated > expectedLength) {
                // cut: keep points up to the expected length, end exactly there
                int i = 1;
                while (i < cumulative.size() && cumulative.get(i) < expectedLength) i++;
                float[] end = lerpOnSegment(i, expectedLength);
                while (points.size() > i) { points.remove(points.size() - 1); cumulative.remove(cumulative.size() - 1); }
                points.add(end);
                cumulative.add(expectedLength);
            } else {
                // extend the last segment in its direction
                float[] a = points.get(points.size() - 2), b = points.get(points.size() - 1);
                double seg = dist(a, b);
                if (seg > 0) {
                    double extra = expectedLength - calculated;
                    float[] end = {(float) (b[0] + (b[0] - a[0]) / seg * extra), (float) (b[1] + (b[1] - a[1]) / seg * extra)};
                    points.add(end);
                    cumulative.add(expectedLength);
                }
            }
        }
        this.length = cumulative.get(cumulative.size() - 1);
    }

    /** headX/headY: slider position, curve: the "B|x:y|x:y" field, length: pixel length from the file. */
    public static SliderPath parse(float headX, float headY, String curve, double length) {
        String[] parts = curve.split("\\|");
        char type = parts.length > 0 && !parts[0].isEmpty() ? Character.toUpperCase(parts[0].charAt(0)) : 'B';
        List<float[]> control = new ArrayList<>();
        control.add(new float[]{headX, headY});
        for (int i = 1; i < parts.length; i++) {
            String[] xy = parts[i].split(":");
            if (xy.length < 2) continue;
            try {
                control.add(new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
            } catch (NumberFormatException ignored) {}
        }
        List<float[]> out;
        switch (type) {
            case 'L' -> out = new ArrayList<>(control);
            case 'P' -> out = control.size() == 3 ? circularArc(control) : bezierSegments(control);
            case 'C' -> out = catmull(control);
            default -> out = bezierSegments(control);
        }
        if (out == null) out = bezierSegments(control);
        return new SliderPath(out, length);
    }

    public double getLength() { return length; }

    /** Point at the given fraction (0..1) of one span. */
    public float[] positionAt(double progress) {
        double d = Math.max(0, Math.min(1, progress)) * length;
        int i = 1;
        while (i < cumulative.size() - 1 && cumulative.get(i) < d) i++;
        if (points.size() == 1) return points.get(0).clone();
        return lerpOnSegment(i, d);
    }

    /** Point at the end of the whole slider: the tail for an odd number of spans, the head otherwise. */
    public float[] endPosition(int spans) {
        return spans % 2 == 1 ? positionAt(1) : positionAt(0);
    }

    /** Points of the path, thinned to at most about maxPoints, for drawing. */
    public List<float[]> drawPoints(int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<float[]> out = new ArrayList<>();
        double step = (double) (points.size() - 1) / (maxPoints - 1);
        for (int k = 0; k < maxPoints; k++) out.add(points.get((int) Math.round(k * step)));
        return out;
    }

    private float[] lerpOnSegment(int i, double d) {
        float[] a = points.get(i - 1), b = points.get(i);
        double segStart = cumulative.get(i - 1), segLen = cumulative.get(i) - segStart;
        double t = segLen > 0 ? (d - segStart) / segLen : 0;
        return new float[]{(float) (a[0] + (b[0] - a[0]) * t), (float) (a[1] + (b[1] - a[1]) * t)};
    }

    private static double dist(float[] a, float[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ---- curve types ----

    private static List<float[]> bezierSegments(List<float[]> control) {
        // a point repeated twice ends one bezier segment and starts the next one
        List<float[]> out = new ArrayList<>();
        List<float[]> seg = new ArrayList<>();
        for (int i = 0; i < control.size(); i++) {
            seg.add(control.get(i));
            boolean last = i == control.size() - 1;
            if (last || dist(control.get(i), control.get(i + 1)) < 1e-4) {
                out.addAll(seg.size() == 1 ? seg : bezier(seg));
                seg = new ArrayList<>();
            }
        }
        return out;
    }

    private static List<float[]> bezier(List<float[]> pts) {
        double polygon = 0;
        for (int i = 1; i < pts.size(); i++) polygon += dist(pts.get(i - 1), pts.get(i));
        int steps = (int) Math.max(8, Math.min(2000, polygon / 2));
        List<float[]> out = new ArrayList<>();
        int n = pts.size();
        double[] xs = new double[n], ys = new double[n];
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            for (int k = 0; k < n; k++) { xs[k] = pts.get(k)[0]; ys[k] = pts.get(k)[1]; }
            for (int r = 1; r < n; r++) {
                for (int k = 0; k < n - r; k++) {
                    xs[k] = xs[k] * (1 - t) + xs[k + 1] * t;
                    ys[k] = ys[k] * (1 - t) + ys[k + 1] * t;
                }
            }
            out.add(new float[]{(float) xs[0], (float) ys[0]});
        }
        return out;
    }

    private static List<float[]> circularArc(List<float[]> p) {
        double ax = p.get(0)[0], ay = p.get(0)[1];
        double bx = p.get(1)[0], by = p.get(1)[1];
        double cx = p.get(2)[0], cy = p.get(2)[1];
        double d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
        if (Math.abs(d) < 1e-3) return null; // points on one line: the game falls back to bezier
        double aSq = ax * ax + ay * ay, bSq = bx * bx + by * by, cSq = cx * cx + cy * cy;
        double ox = (aSq * (by - cy) + bSq * (cy - ay) + cSq * (ay - by)) / d;
        double oy = (aSq * (cx - bx) + bSq * (ax - cx) + cSq * (bx - ax)) / d;
        double r = Math.hypot(ax - ox, ay - oy);
        double thetaStart = Math.atan2(ay - oy, ax - ox);
        double thetaEnd = Math.atan2(cy - oy, cx - ox);
        while (thetaEnd < thetaStart) thetaEnd += 2 * Math.PI;
        double dir = 1;
        double range = thetaEnd - thetaStart;
        // the arc has to pass through the middle point; if not, go the other way round
        double orthoX = cy - ay, orthoY = -(cx - ax);
        if (orthoX * (bx - ax) + orthoY * (by - ay) < 0) {
            dir = -1;
            range = 2 * Math.PI - range;
        }
        int steps = r * range < 0.2 ? 2 : (int) Math.max(2, Math.min(1000, Math.ceil(range / (2 * Math.acos(Math.max(-1, 1 - 0.1 / r))))));
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            double theta = thetaStart + dir * (double) i / (steps - 1) * range;
            out.add(new float[]{(float) (ox + Math.cos(theta) * r), (float) (oy + Math.sin(theta) * r)});
        }
        return out;
    }

    private static List<float[]> catmull(List<float[]> p) {
        List<float[]> out = new ArrayList<>();
        int detail = 50;
        for (int i = 0; i < p.size() - 1; i++) {
            float[] v1 = i > 0 ? p.get(i - 1) : p.get(i);
            float[] v2 = p.get(i);
            float[] v3 = i < p.size() - 1 ? p.get(i + 1) : new float[]{v2[0] * 2 - v1[0], v2[1] * 2 - v1[1]};
            float[] v4 = i < p.size() - 2 ? p.get(i + 2) : new float[]{v3[0] * 2 - v2[0], v3[1] * 2 - v2[1]};
            for (int c = 0; c < detail; c++) {
                out.add(catmullPoint(v1, v2, v3, v4, (double) c / detail));
                out.add(catmullPoint(v1, v2, v3, v4, (double) (c + 1) / detail));
            }
        }
        if (out.isEmpty()) out.addAll(p);
        return out;
    }

    private static float[] catmullPoint(float[] a, float[] b, float[] c, float[] d, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * (2 * b[0] + (-a[0] + c[0]) * t + (2 * a[0] - 5 * b[0] + 4 * c[0] - d[0]) * t2 + (-a[0] + 3 * b[0] - 3 * c[0] + d[0]) * t3);
        double y = 0.5 * (2 * b[1] + (-a[1] + c[1]) * t + (2 * a[1] - 5 * b[1] + 4 * c[1] - d[1]) * t2 + (-a[1] + 3 * b[1] - 3 * c[1] + d[1]) * t3);
        return new float[]{(float) x, (float) y};
    }
}
