package dev.osunolimits.modules.utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import spark.Request;
import spark.Response;

/**
 * Website translations.
 *
 * Texts live in lang/&lt;code&gt;.properties (UTF-8) next to templates/ and docs/,
 * so they can be edited on the server without rebuilding. en.properties is the
 * base: a key missing in another language falls back to English, so a partial
 * translation never breaks a page. Files are re-read when they change on disk.
 *
 * In templates: ${t["nav.home"]}. The current language code is ${lang}.
 */
public final class I18n {

    public static final String DEFAULT_LANG = "en";
    public static final String COOKIE = "lang";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

    private static Path dir = Paths.get("lang");

    private static final class Loaded {
        final long stamp;
        final Map<String, String> messages;

        Loaded(long stamp, Map<String, String> messages) {
            this.stamp = stamp;
            this.messages = messages;
        }
    }

    private static final Map<String, Loaded> cache = new ConcurrentHashMap<>();
    private static volatile Map<String, String> languagesCache = Collections.emptyMap();
    private static volatile long languagesStamp = -1;

    private I18n() {
    }

    /** For tests: read translation files from another directory. */
    public static void setDirectory(Path directory) {
        dir = directory;
        cache.clear();
        languagesStamp = -1;
    }

    private static long stamp(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis() ^ Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    private static Map<String, String> readFile(Path p) {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            return new HashMap<>();
        }
        Map<String, String> out = new HashMap<>();
        for (String k : props.stringPropertyNames()) {
            out.put(k, props.getProperty(k));
        }
        return out;
    }

    private static Map<String, String> raw(String code) {
        Path p = dir.resolve(code + ".properties");
        long s = stamp(p);
        if (s == -1) {
            return Collections.emptyMap();
        }
        Loaded l = cache.get(code);
        if (l == null || l.stamp != s) {
            l = new Loaded(s, Collections.unmodifiableMap(readFile(p)));
            cache.put(code, l);
        }
        return l.messages;
    }

    /** All messages for a language, with English filling every missing key. */
    public static Map<String, String> messages(String code) {
        Map<String, String> base = raw(DEFAULT_LANG);
        if (code == null || code.equals(DEFAULT_LANG)) {
            return base;
        }
        Map<String, String> merged = new HashMap<>(base);
        for (Map.Entry<String, String> e : raw(code).entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return merged;
    }

    /** Available languages: code to native name (key "lang.name" in each file), English first. */
    public static Map<String, String> languages() {
        long s = stamp(dir);
        if (s != languagesStamp) {
            Map<String, String> found = new LinkedHashMap<>();
            if (Files.exists(dir.resolve(DEFAULT_LANG + ".properties"))) {
                found.put(DEFAULT_LANG, raw(DEFAULT_LANG).getOrDefault("lang.name", "English"));
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.properties")) {
                java.util.List<String> codes = new java.util.ArrayList<>();
                for (Path p : ds) {
                    String code = p.getFileName().toString().replace(".properties", "");
                    if (code.matches("[a-z]{2}") && !code.equals(DEFAULT_LANG)) {
                        codes.add(code);
                    }
                }
                Collections.sort(codes);
                for (String code : codes) {
                    found.put(code, raw(code).getOrDefault("lang.name", code));
                }
            } catch (IOException e) {
                // no lang directory: English only
            }
            languagesCache = Collections.unmodifiableMap(found);
            languagesStamp = s;
        }
        return languagesCache;
    }

    public static boolean isSupported(String code) {
        return code != null && languages().containsKey(code);
    }

    /** Cookie first, then the browser's Accept-Language, then English. */
    public static String detect(Request req) {
        String fromCookie = req.cookie(COOKIE);
        if (isSupported(fromCookie)) {
            return fromCookie;
        }
        String header = req.headers("Accept-Language");
        if (header != null) {
            for (String part : header.split(",")) {
                String tag = part.split(";")[0].trim().toLowerCase(Locale.ROOT);
                if (tag.length() >= 2) {
                    String code = tag.substring(0, 2);
                    if (isSupported(code)) {
                        return code;
                    }
                }
            }
        }
        return DEFAULT_LANG;
    }

    /** GET /lang/:code - remember the choice and go back to the page the user was on. */
    public static Object handleSwitch(Request req, Response res) {
        String code = req.params(":code");
        if (isSupported(code)) {
            res.cookie("/", COOKIE, code, COOKIE_MAX_AGE, true, false);
        }
        String back = req.queryParams("back");
        if (back == null || !back.startsWith("/") || back.startsWith("//") || back.contains("\\")) {
            back = "/";
        }
        res.redirect(back);
        return "";
    }
}
