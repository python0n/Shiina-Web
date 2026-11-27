package dev.osunolimits.modules.osr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Parser for .osu (osu! beatmap format)
 * Spec: https://osu.ppy.sh/wiki/en/Client/File_formats/osu_(file_format)
 */
public class OsuFileParser {

    @Data
    public static class OsuData {
        private String title;
        private String artist;
        private String version;    // nazwa trudności
        private int formatVersion = 14;
        private float ar = -1;
        private float od = 5;
        private float cs = 5;
        private float hp = 5;
        private int mode;
        private float stackLeniency = 0.7f;
        private double sliderMultiplier = 1.4;
        private List<HitObject> hitObjects = new ArrayList<>();
        private List<TimingPoint> timingPoints = new ArrayList<>();
    }

    @Data
    public static class HitObject {
        private float x;
        private float y;
        private long timeMs;       // czas uderzenia w ms
        private int type;          // bitmask: 1=circle, 2=slider, 8=spinner, 128=mania hold
        private int hitSound;
        private long endTimeMs;    // end of a slider, spinner or hold note; = timeMs otherwise
        // sliders
        private String curve;
        private int slides = 1;
        private double pixelLength;
        private transient SliderPath path;
        private float endX;        // where the slider ends (tail or head, depending on repeats)
        private float endY;
        // filled in by Stacking
        private int stackHeight;

        public boolean isCircle() { return (type & 1) != 0 && (type & 8) == 0; }
        public boolean isSlider() { return (type & 2) != 0; }
        public boolean isSpinner() { return (type & 8) != 0; }
        public boolean isHold() { return (type & 128) != 0; }
        public boolean isNewCombo() { return (type & 4) != 0; }
    }

    @Data
    public static class TimingPoint {
        private double time;
        private long timeMs;
        private double beatLength;  // ms per beat (ujemna = inherited)
        private int meter;
        private boolean inherited;

        public double getBpm() {
            if (inherited || beatLength <= 0) return 0;
            return 60000.0 / beatLength;
        }

        // hit window OD (ms) dla 300/100/50
        public static int[] getHitWindows(float od) {
            int w300 = (int)(80 - 6 * od);
            int w100 = (int)(140 - 8 * od);
            int w50  = (int)(200 - 10 * od);
            return new int[]{w300, w100, w50};
        }
    }

    // the .osu file may come from a player (map upload), and the analysis walks
    // the objects several times, so its size is bounded
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_HIT_OBJECTS = 30_000;
    private static final int MAX_TIMING_POINTS = 20_000;
    private static final double MAX_SLIDER_LENGTH = 100_000;

    public static OsuData parse(String filePath) throws IOException {
        if (new java.io.File(filePath).length() > MAX_FILE_BYTES) {
            throw new IOException("Beatmap file is too large to analyze");
        }
        OsuData data = new OsuData();
        String section = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Path.of(filePath)), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (first) {
                    first = false;
                    line = line.replace("﻿", "");
                    if (line.startsWith("osu file format v")) {
                        try { data.formatVersion = Integer.parseInt(line.substring(17).trim()); } catch (NumberFormatException ignored) {}
                        continue;
                    }
                }
                if (line.isEmpty() || line.startsWith("//")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1);
                    continue;
                }

                switch (section) {
                    case "Metadata" -> parseMetadata(line, data);
                    case "Difficulty" -> parseDifficulty(line, data);
                    case "General" -> parseGeneral(line, data);
                    case "TimingPoints" -> parseTimingPoint(line, data);
                    case "HitObjects" -> parseHitObject(line, data);
                }
                if (data.hitObjects.size() > MAX_HIT_OBJECTS || data.timingPoints.size() > MAX_TIMING_POINTS) {
                    throw new IOException("Beatmap has too many objects to analyze");
                }
            }
        }
        if (data.ar < 0) data.ar = data.od; // very old maps: AR was the same as OD
        data.timingPoints.sort((a, b) -> Double.compare(a.time, b.time));
        computeSliders(data);
        return data;
    }

    private static void parseMetadata(String line, OsuData data) {
        if (line.startsWith("Title:")) data.title = line.substring(6).trim();
        else if (line.startsWith("Artist:")) data.artist = line.substring(7).trim();
        else if (line.startsWith("Version:")) data.version = line.substring(8).trim();
    }

    private static float num(String line, int from) {
        return Float.parseFloat(line.substring(from).trim());
    }

    private static void parseDifficulty(String line, OsuData data) {
        try {
            if (line.startsWith("ApproachRate:")) data.ar = num(line, 13);
            else if (line.startsWith("OverallDifficulty:")) data.od = num(line, 18);
            else if (line.startsWith("CircleSize:")) data.cs = num(line, 11);
            else if (line.startsWith("HPDrainRate:")) data.hp = num(line, 12);
            else if (line.startsWith("SliderMultiplier:")) data.sliderMultiplier = num(line, 17);
        } catch (NumberFormatException ignored) {}
    }

    private static void parseGeneral(String line, OsuData data) {
        try {
            if (line.startsWith("Mode:")) data.mode = Integer.parseInt(line.substring(5).trim());
            else if (line.startsWith("StackLeniency:")) data.stackLeniency = num(line, 14);
        } catch (NumberFormatException ignored) {}
    }

    private static void parseTimingPoint(String line, OsuData data) {
        String[] parts = line.split(",");
        if (parts.length < 2) return;
        try {
            TimingPoint tp = new TimingPoint();
            tp.time = Double.parseDouble(parts[0].trim());
            tp.timeMs = (long) tp.time;
            tp.beatLength = Double.parseDouble(parts[1].trim());
            tp.meter = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 4;
            // field 7 says if the point is uninherited; old files don't have it
            if (parts.length > 6) tp.inherited = "0".equals(parts[6].trim());
            else tp.inherited = tp.beatLength < 0;
            data.timingPoints.add(tp);
        } catch (NumberFormatException ignored) {}
    }

    private static void parseHitObject(String line, OsuData data) {
        String[] parts = line.split(",");
        if (parts.length < 5) return;
        try {
            HitObject ho = new HitObject();
            ho.x = Float.parseFloat(parts[0].trim());
            ho.y = Float.parseFloat(parts[1].trim());
            ho.timeMs = (long) Double.parseDouble(parts[2].trim());
            ho.type = Integer.parseInt(parts[3].trim());
            ho.hitSound = Integer.parseInt(parts[4].trim());
            ho.endTimeMs = ho.timeMs;
            ho.endX = ho.x;
            ho.endY = ho.y;
            if (ho.isSlider() && parts.length > 7) {
                ho.curve = parts[5].trim();
                ho.slides = Math.max(1, Math.min(10_000, Integer.parseInt(parts[6].trim())));
                ho.pixelLength = Math.max(0, Math.min(MAX_SLIDER_LENGTH, Double.parseDouble(parts[7].trim())));
            } else if (ho.isSpinner() && parts.length > 5) {
                ho.endTimeMs = Math.max(ho.timeMs, (long) Double.parseDouble(parts[5].trim()));
            } else if (ho.isHold() && parts.length > 5) {
                String end = parts[5].split(":")[0].trim();
                ho.endTimeMs = Math.max(ho.timeMs, (long) Double.parseDouble(end));
            }
            data.hitObjects.add(ho);
        } catch (NumberFormatException ignored) {}
    }

    /** Beat length (ms) and slider velocity multiplier in force at the given time. */
    private static double[] timingAt(OsuData data, double time) {
        double beatLength = 500, sv = 1;
        boolean found = false;
        for (TimingPoint tp : data.timingPoints) {
            if (tp.time > time) break;
            if (!tp.inherited) {
                beatLength = tp.beatLength;
                sv = 1;
                found = true;
            } else if (tp.beatLength < 0) {
                sv = Math.max(0.1, Math.min(10, -100.0 / tp.beatLength));
            }
        }
        if (!found) {
            // before the first timing point the first red line counts
            for (TimingPoint tp : data.timingPoints) {
                if (!tp.inherited) { beatLength = tp.beatLength; break; }
            }
        }
        return new double[]{beatLength, sv};
    }

    private static void computeSliders(OsuData data) {
        for (HitObject o : data.hitObjects) {
            if (!o.isSlider() || o.curve == null) continue;
            o.path = SliderPath.parse(o.x, o.y, o.curve, o.pixelLength);
            double[] tm = timingAt(data, o.timeMs);
            double velocity = 100 * data.sliderMultiplier * tm[1] / tm[0]; // px per ms
            double span = velocity > 0 ? o.path.getLength() / velocity : 0;
            o.endTimeMs = o.timeMs + Math.round(span * o.slides);
            float[] end = o.path.endPosition(o.slides);
            o.endX = end[0];
            o.endY = end[1];
        }
    }

    /**
     * Znajdź BPM obowiązujący w danym czasie
     */
    public static double getBpmAt(OsuData data, long timeMs) {
        double bpm = 0;
        for (TimingPoint tp : data.timingPoints) {
            if (tp.timeMs > timeMs) break;
            if (!tp.inherited) bpm = tp.getBpm();
        }
        if (bpm == 0) {
            for (TimingPoint tp : data.timingPoints) {
                if (!tp.inherited && tp.beatLength > 0) return tp.getBpm();
            }
        }
        return bpm;
    }

    /**
     * Czy dana nuta jest częścią streamu?
     * Stream = >= 4 kolejne nuty w odstępie <= 1/4 beatu przy BPM >= 140
     */
    public static boolean isInStream(OsuData data, int hitObjectIndex, int minStreamLength) {
        List<HitObject> objects = data.hitObjects;
        if (hitObjectIndex < 0 || hitObjectIndex >= objects.size()) return false;
        HitObject current = objects.get(hitObjectIndex);
        if (!current.isCircle()) return false;
        double bpm = getBpmAt(data, current.timeMs);
        if (bpm < 140) return false;
        double quarterBeat = 60000.0 / bpm / 4;
        double tolerance = quarterBeat * 0.25;
        // znajdz poczatek streamu (idz wstecz)
        int start = hitObjectIndex;
        for (int i = hitObjectIndex - 1; i >= 0; i--) {
            if (!objects.get(i).isCircle()) break;
            long gap = objects.get(i + 1).timeMs - objects.get(i).timeMs;
            if (Math.abs(gap - quarterBeat) <= tolerance) start = i;
            else break;
        }
        // licz streak od poczatku w przod
        int streak = 1;
        for (int i = start + 1; i < objects.size(); i++) {
            if (!objects.get(i).isCircle()) break;
            long gap = objects.get(i).timeMs - objects.get(i - 1).timeMs;
            if (Math.abs(gap - quarterBeat) <= tolerance) streak++;
            else break;
        }
        return streak >= minStreamLength;
    }
}
