package dev.osunolimits.modules.utils;

import java.util.Locale;
import java.util.Map;

import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;

/**
 * Short texts for link previews (og:description), e.g. what Discord shows under a pasted link.
 * The wording lives in lang/*.properties under seo.*, with {name} placeholders.
 * A crawler sends no language cookie, so it usually gets the default language.
 */
public final class Embed {

    private Embed() {
    }

    /** seo.* text in the language of this request, placeholders given as name, value, name, value... */
    public static String text(ShiinaRequest shiina, String key, Object... pairs) {
        Object lang = shiina.data.get("lang");
        Map<String, String> t = I18n.messages(lang == null ? null : lang.toString());
        String out = t.getOrDefault(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return out;
    }

    public static String mode(int mode) {
        switch (mode) {
            case 0: return "osu!";
            case 1: return "osu!taiko";
            case 2: return "osu!catch";
            case 3: return "osu!mania";
            case 4: return "osu! relax";
            case 5: return "osu!taiko relax";
            case 6: return "osu!catch relax";
            case 8: return "osu! autopilot";
            default: return "osu!";
        }
    }

    /** bancho.py map status as a seo.status.* text */
    public static String status(ShiinaRequest shiina, int status) {
        switch (status) {
            case 2: return text(shiina, "seo.status.ranked");
            case 3: return text(shiina, "seo.status.approved");
            case 4: return text(shiina, "seo.status.qualified");
            case 5: return text(shiina, "seo.status.loved");
            default: return text(shiina, "seo.status.unranked");
        }
    }

    public static String decimal(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    /** seconds as m:ss */
    public static String length(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /** XH/X are SS and SH is S, the silver letters only differ in colour */
    public static String grade(String grade) {
        if (grade == null) {
            return "?";
        }
        switch (grade) {
            case "XH":
            case "X": return "SS";
            case "SH": return "S";
            default: return grade;
        }
    }

    /** " +HDDT" or "" for no mods */
    public static String mods(String[] mods) {
        if (mods == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String m : mods) {
            if (m != null && !m.isBlank() && !m.equals("NM") && !m.equals("None")) {
                sb.append(m);
            }
        }
        return sb.length() == 0 ? "" : " +" + sb;
    }
}
