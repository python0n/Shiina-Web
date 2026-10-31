package dev.osunolimits.utils;

/**
 * Version numbers added to static URLs (?v=...), so browsers and nginx can
 * cache css/js/avatars for a long time and still get new files right away:
 * assets change with every restart (deploy), avatars whenever one is changed.
 */
public final class AssetVersion {

    private static final String STARTUP = String.valueOf(System.currentTimeMillis() / 1000);
    private static volatile long avatars = System.currentTimeMillis() / 1000;

    private AssetVersion() {}

    public static String assets() {
        return STARTUP;
    }

    public static String avatars() {
        return String.valueOf(avatars);
    }

    /** Call after an avatar was uploaded or deleted. */
    public static void avatarChanged() {
        avatars = System.currentTimeMillis() / 1000;
    }
}
