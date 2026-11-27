package dev.osunolimits.modules.osr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Music and background of a beatmap set for the replay player. The .osz is downloaded once
 * from the mirror, only the audio file and the background are kept (in data/analyzer-media),
 * and the oldest sets are removed when there are too many.
 */
public final class BeatmapMedia {

    private static final Logger log = LoggerFactory.getLogger(BeatmapMedia.class);
    private static final Path DIR = Path.of("data", "analyzer-media");
    private static final long MAX_OSZ_BYTES = 80L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 40L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;
    private static final int MAX_SETS = 300;
    private static final ConcurrentHashMap<Integer, Object> LOCKS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private BeatmapMedia() {}

    /** Mirror URL for a set: ANALYZER_OSZ_URL from .env with {id}, or the server's own /d/ proxy. */
    private static String oszUrl(String template, int setId) {
        String t = template == null || template.isBlank() ? "https://c.taksiegra.ovh/d/{id}" : template;
        return t.replace("{id}", String.valueOf(setId));
    }

    /** File names of the audio and the background, read from the .osu file. */
    public static String[] namesFromOsu(String osuPath) throws IOException {
        String audio = null, bg = null, section = "";
        try (BufferedReader r = Files.newBufferedReader(Path.of(osuPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("[") && line.endsWith("]")) { section = line; continue; }
                if (section.equals("[General]") && line.startsWith("AudioFilename:")) audio = line.substring(14).trim();
                if (section.equals("[Events]") && bg == null && (line.startsWith("0,0,") || line.startsWith("Background,"))) {
                    int a = line.indexOf('"'), b = line.indexOf('"', a + 1);
                    if (a >= 0 && b > a) bg = line.substring(a + 1, b);
                }
                if (section.equals("[HitObjects]")) break;
            }
        }
        return new String[]{audio, bg};
    }

    /**
     * Path of the cached file ("audio" or "bg") for the set, downloading the set first if needed.
     * Returns null when the set or the file cannot be had.
     */
    public static Path get(int setId, String osuPath, String kind, String urlTemplate) {
        if (setId <= 0) return null;
        Object lock = LOCKS.computeIfAbsent(setId, k -> new Object());
        synchronized (lock) {
            try {
                Path setDir = DIR.resolve(String.valueOf(setId));
                Path found = find(setDir, kind);
                if (found != null) {
                    setDir.toFile().setLastModified(System.currentTimeMillis());
                    return found;
                }
                // a failed download is remembered for an hour, so a missing set does not hit the mirror every time
                Path failed = setDir.resolve(".failed");
                if (Files.exists(failed) && System.currentTimeMillis() - Files.getLastModifiedTime(failed).toMillis() < 3_600_000) return null;
                String[] names = namesFromOsu(osuPath);
                Files.createDirectories(setDir);
                if (!download(setId, names, setDir, urlTemplate)) {
                    Files.writeString(failed, "");
                    return null;
                }
                Files.deleteIfExists(failed);
                cleanup();
                return find(setDir, kind);
            } catch (Exception e) {
                log.warn("Beatmap media for set {} failed: {}", setId, e.getMessage());
                return null;
            }
        }
    }

    private static Path find(Path setDir, String kind) throws IOException {
        if (!Files.isDirectory(setDir)) return null;
        try (var s = Files.list(setDir)) {
            return s.filter(p -> p.getFileName().toString().startsWith(kind + ".")).findFirst().orElse(null);
        }
    }

    private static String ext(String name, String fallback) {
        int i = name.lastIndexOf('.');
        String e = i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
        return e.matches("mp3|ogg|wav|jpg|jpeg|png") ? e : fallback;
    }

    private static boolean download(int setId, String[] names, Path setDir, String urlTemplate) throws Exception {
        if (names[0] == null) return false;
        HttpRequest req = HttpRequest.newBuilder(URI.create(oszUrl(urlTemplate, setId)))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "Shiina-Web analyzer")
            .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            log.info("Beatmap media: mirror returned {} for set {}", res.statusCode(), setId);
            return false;
        }
        boolean gotAudio = false;
        try (InputStream raw = new LimitedInputStream(res.body(), MAX_OSZ_BYTES); ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!gotAudio && name.equalsIgnoreCase(names[0])) {
                    gotAudio = save(zip, setDir.resolve("audio." + ext(name, "mp3")), MAX_AUDIO_BYTES);
                } else if (names[1] != null && name.equalsIgnoreCase(names[1])) {
                    save(zip, setDir.resolve("bg." + ext(name, "jpg")), MAX_IMAGE_BYTES);
                }
            }
        }
        return gotAudio;
    }

    private static boolean save(InputStream in, Path target, long max) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        long total = 0;
        byte[] buf = new byte[65536];
        try (OutputStream out = Files.newOutputStream(tmp)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) { out.close(); Files.deleteIfExists(tmp); return false; }
                out.write(buf, 0, n);
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static void cleanup() {
        File[] sets = DIR.toFile().listFiles(File::isDirectory);
        if (sets == null || sets.length <= MAX_SETS) return;
        Arrays.sort(sets, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < sets.length - MAX_SETS; i++) {
            File[] files = sets[i].listFiles();
            if (files != null) for (File f : files) f.delete();
            sets[i].delete();
        }
    }

    /** Stops reading after a byte limit, so a huge download cannot fill the disk or memory. */
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long left;
        LimitedInputStream(InputStream in, long limit) { super(in); left = limit; }
        @Override public int read() throws IOException {
            if (left <= 0) throw new IOException("download too large");
            int b = super.read();
            if (b >= 0) left--;
            return b;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) throw new IOException("download too large");
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }

    public static String contentType(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }
}
