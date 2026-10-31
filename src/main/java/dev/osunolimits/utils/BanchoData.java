package dev.osunolimits.utils;

import dev.osunolimits.main.App;

/**
 * Location of bancho's .data folder (replays, .osu files). Set BANCHO_DATA_DIR
 * in .config/.env; the default is the path used on taksiegra.
 */
public final class BanchoData {

    private static final String DEFAULT_DIR = "/mnt/storage/osu/bancho-py-ex/.data/";

    private BanchoData() {}

    public static String dir() {
        String dir = App.env.get("BANCHO_DATA_DIR");
        if (dir == null || dir.isBlank()) {
            dir = DEFAULT_DIR;
        }
        return dir.endsWith("/") ? dir : dir + "/";
    }

    public static String replays() {
        return dir() + "osr/";
    }

    public static String pendingReplays() {
        return dir() + "osr/pending/";
    }

    public static String beatmaps() {
        return dir() + "osu/";
    }
}
