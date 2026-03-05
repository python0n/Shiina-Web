package dev.osunolimits.modules.osr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
        private float ar;
        private float od;
        private float cs;
        private float hp;
        private int mode;
        private List<HitObject> hitObjects = new ArrayList<>();
        private List<TimingPoint> timingPoints = new ArrayList<>();
    }

    @Data
    public static class HitObject {
        private float x;
        private float y;
        private long timeMs;       // czas uderzenia w ms
        private int type;          // bitmask: 1=circle, 2=slider, 8=spinner
        private int hitSound;

        public boolean isCircle() { return (type & 1) != 0 && (type & 8) == 0; }
        public boolean isSlider() { return (type & 2) != 0; }
        public boolean isSpinner() { return (type & 8) != 0; }
        public boolean isNewCombo() { return (type & 4) != 0; }
    }

    @Data
    public static class TimingPoint {
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

    public static OsuData parse(String filePath) throws IOException {
        OsuData data = new OsuData();
        String section = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
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
            }
        }

        return data;
    }

    private static void parseMetadata(String line, OsuData data) {
        if (line.startsWith("Title:")) data.title = line.substring(6).trim();
        else if (line.startsWith("Artist:")) data.artist = line.substring(7).trim();
        else if (line.startsWith("Version:")) data.version = line.substring(8).trim();
    }

    private static void parseDifficulty(String line, OsuData data) {
        if (line.startsWith("ApproachRate:")) data.ar = Float.parseFloat(line.substring(13).trim());
        else if (line.startsWith("OverallDifficulty:")) data.od = Float.parseFloat(line.substring(18).trim());
        else if (line.startsWith("CircleSize:")) data.cs = Float.parseFloat(line.substring(11).trim());
        else if (line.startsWith("HPDrainRate:")) data.hp = Float.parseFloat(line.substring(12).trim());
    }

    private static void parseGeneral(String line, OsuData data) {
        if (line.startsWith("Mode:")) data.mode = Integer.parseInt(line.substring(5).trim());
    }

    private static void parseTimingPoint(String line, OsuData data) {
        String[] parts = line.split(",");
        if (parts.length < 2) return;
        try {
            TimingPoint tp = new TimingPoint();
            tp.timeMs = (long) Double.parseDouble(parts[0].trim());
            tp.beatLength = Double.parseDouble(parts[1].trim());
            tp.meter = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 4;
            tp.inherited = tp.beatLength < 0;
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
            ho.timeMs = Long.parseLong(parts[2].trim());
            ho.type = Integer.parseInt(parts[3].trim());
            ho.hitSound = Integer.parseInt(parts[4].trim());
            data.hitObjects.add(ho);
        } catch (NumberFormatException ignored) {}
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
