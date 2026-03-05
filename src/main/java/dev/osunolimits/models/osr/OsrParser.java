package dev.osunolimits.modules.osr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.tukaani.xz.LZMAInputStream;

import lombok.Data;

/**
 * Parser for .osr (osu! replay format)
 * Spec: https://osu.ppy.sh/wiki/en/Client/File_formats/osr_(file_format)
 */
public class OsrParser {

    @Data
    public static class OsrData {
        private int mode;
        private int gameVersion;
        private String mapMd5;
        private String playerName;
        private String replayMd5;
        private int n300;
        private int n100;
        private int n50;
        private int ngeki;
        private int nkatu;
        private int nmiss;
        private int score;
        private int maxCombo;
        private boolean perfect;
        private int mods;
        private long timestamp;
        private long scoreId;

        // obliczone
        private float acc;
        private String grade;

        // dane kliknięć
        private List<ReplayFrame> frames = new ArrayList<>();
    }

    @Data
    public static class ReplayFrame {
        private long timeMs;      // absolutny czas w ms od startu
        private float x;          // pozycja X kursora
        private float y;          // pozycja Y kursora
        private int keys;         // bitmask: 1=M1, 2=M2, 4=K1, 8=K2, 16=smoke

        public boolean isClick() {
            return (keys & 0x0F) != 0;
        }

        public boolean wasClickedBefore(ReplayFrame prev) {
            if (prev == null) return isClick();
            // rising edge per-klawisz: wykrywa alternate (K1/K2 na zmianę)
            int cur  = keys  & 0x0F;
            int prv  = prev.keys & 0x0F;
            return (cur & ~prv) != 0;
        }
    }

    public static OsrData parse(byte[] data) throws IOException {
        return parse(data, false);
    }

    public static OsrData parse(byte[] data, boolean parseFrames) throws IOException {
        // bancho-py zapisuje tylko skompresowane klatki (raw LZMA)
        // wykryj po nagłówku LZMA: 0x5d
        if (data.length > 0 && (data[0] & 0xFF) == 0x5d) {
            OsrData osr = new OsrData();
            if (parseFrames) {
                try {
                    String replayStr = decompressLZMA(data);
                    osr.frames = parseReplayFrames(replayStr);
                } catch (Exception e) {
                    // zostaw pustą listę
                }
            }
            return osr;
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        OsrData osr = new OsrData();

        osr.mode = readByte(stream);
        osr.gameVersion = readInt(stream);
        osr.mapMd5 = readString(stream);
        osr.playerName = readString(stream);
        osr.replayMd5 = readString(stream);
        osr.n300 = readShort(stream);
        osr.n100 = readShort(stream);
        osr.n50 = readShort(stream);
        osr.ngeki = readShort(stream);
        osr.nkatu = readShort(stream);
        osr.nmiss = readShort(stream);
        osr.score = readInt(stream);
        osr.maxCombo = readShort(stream);
        osr.perfect = readByte(stream) == 1;
        osr.mods = readInt(stream);

        // life bar — skip
        readString(stream);

        osr.timestamp = readLong(stream);

        // compressed replay data
        int replayLen = readInt(stream);
        if (replayLen > 0) {
            if (parseFrames) {
                byte[] compressed = stream.readNBytes(replayLen);
                try {
                    String replayStr = decompressLZMA(compressed);
                    osr.frames = parseReplayFrames(replayStr);
                } catch (Exception e) {
                    // if decompression fails, leave an empty list
                }
            } else {
                stream.skip(replayLen);
            }
        }

        try {
            osr.scoreId = readLong(stream);
        } catch (Exception e) {
            osr.scoreId = 0;
        }

        osr.acc = calculateAcc(osr);
        osr.grade = calculateGrade(osr);

        return osr;
    }

    private static String decompressLZMA(byte[] compressed) throws IOException {
        // osu! używa LZMA bez nagłówka XZ — raw LZMA stream
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        LZMAInputStream lzma = new LZMAInputStream(bis);
        byte[] decompressed = lzma.readAllBytes();
        lzma.close();
        return new String(decompressed, StandardCharsets.UTF_8);
    }

    private static List<ReplayFrame> parseReplayFrames(String replayStr) {
        List<ReplayFrame> frames = new ArrayList<>();
        long absoluteTime = 0;

        String[] parts = replayStr.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String[] fields = part.split("\\|");
            if (fields.length < 4) continue;

            try {
                long delta = Long.parseLong(fields[0].trim());
                float x = Float.parseFloat(fields[1].trim());
                float y = Float.parseFloat(fields[2].trim());
                int keys = (int) Float.parseFloat(fields[3].trim());

                // delta -12345 to seed dla RNG, pomijamy
                if (delta == -12345) continue;

                absoluteTime += delta;

                ReplayFrame frame = new ReplayFrame();
                frame.timeMs = absoluteTime;
                frame.x = x;
                frame.y = y;
                frame.keys = keys;
                frames.add(frame);
            } catch (NumberFormatException e) {
                // skip invalid frames
            }
        }

        return frames;
    }

    private static float calculateAcc(OsrData osr) {
        switch (osr.mode) {
            case 0: {
                float total = osr.n300 + osr.n100 + osr.n50 + osr.nmiss;
                if (total == 0) return 0f;
                return (osr.n300 * 300f + osr.n100 * 100f + osr.n50 * 50f) / (total * 300f) * 100f;
            }
            case 1: {
                float total = osr.n300 + osr.n100 + osr.nmiss;
                if (total == 0) return 0f;
                return (osr.n300 * 300f + osr.n100 * 150f) / (total * 300f) * 100f;
            }
            case 2: {
                float total = osr.n300 + osr.n100 + osr.n50 + osr.nkatu + osr.nmiss;
                if (total == 0) return 0f;
                return (osr.n300 + osr.n100 + osr.n50) / total * 100f;
            }
            case 3: {
                float total = osr.n300 + osr.ngeki + osr.n100 + osr.nkatu + osr.n50 + osr.nmiss;
                if (total == 0) return 0f;
                return (osr.ngeki * 300f + osr.n300 * 300f + osr.nkatu * 200f + osr.n100 * 100f + osr.n50 * 50f)
                        / (total * 300f) * 100f;
            }
            default: return 0f;
        }
    }

    private static String calculateGrade(OsrData osr) {
        float acc = osr.acc;
        int n300 = osr.n300, n100 = osr.n100, n50 = osr.n50, nmiss = osr.nmiss;
        boolean hdOrFl = (osr.mods & (8 | 1024)) != 0;

        if (osr.mode == 0) {
            int total = n300 + n100 + n50 + nmiss;
            if (total == 0) return "F";
            float ratio300 = (float) n300 / total;
            float ratio50 = (float) n50 / total;
            if (nmiss > 0) {
                if (acc < 70) return "D";
                if (acc < 80) return "C";
                if (acc < 90) return "B";
                return "A";
            }
            if (ratio300 == 1f) return hdOrFl ? "XH" : "X";
            if (ratio300 > 0.9f && ratio50 < 0.01f) return hdOrFl ? "SH" : "S";
            if (ratio300 > 0.8f) return "A";
            if (ratio300 > 0.7f) return "B";
            if (ratio300 > 0.6f) return "C";
            return "D";
        }
        if (acc == 100f) return hdOrFl ? "XH" : "X";
        if (acc >= 95f) return hdOrFl ? "SH" : "S";
        if (acc >= 90f) return "A";
        if (acc >= 80f) return "B";
        if (acc >= 70f) return "C";
        return "D";
    }

    // --- readers ---

    private static int readByte(ByteArrayInputStream s) throws IOException {
        int b = s.read();
        if (b == -1) throw new IOException("Unexpected end of stream");
        return b;
    }

    private static int readShort(ByteArrayInputStream s) throws IOException {
        byte[] b = s.readNBytes(2);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int readInt(ByteArrayInputStream s) throws IOException {
        byte[] b = s.readNBytes(4);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static long readLong(ByteArrayInputStream s) throws IOException {
        byte[] b = s.readNBytes(8);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static String readString(ByteArrayInputStream s) throws IOException {
        int exists = readByte(s);
        if (exists == 0x00) return "";
        if (exists != 0x0b) throw new IOException("Invalid string marker: " + exists);

        int len = 0, shift = 0;
        while (true) {
            int b = readByte(s);
            len |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }

        byte[] strBytes = s.readNBytes(len);
        return new String(strBytes, StandardCharsets.UTF_8);
    }
}
