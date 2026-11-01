package dev.osunolimits.utils.osu;

/** Star rating colour scale used by osu-web (same stops and gamma 2.2 interpolation). */
public final class DiffColour {

    private static final double[] STOPS = {0.1, 1.25, 2, 2.5, 3.3, 4.2, 4.9, 5.8, 6.7, 7.7, 9};
    private static final int[] COLOURS = {0x4290FB, 0x4FC0FF, 0x4FFFD5, 0x7CFF4F, 0xF6F05C, 0xFF8068, 0xFF4E6F, 0xC645B8, 0x6563DE, 0x18158E, 0x000000};
    private static final double GAMMA = 2.2;

    private DiffColour() {
    }

    public static String of(double stars) {
        if (stars < 0.1) return "#AAAAAA";
        if (stars >= 9) return "#000000";
        int i = 0;
        while (i < STOPS.length - 2 && stars > STOPS[i + 1]) i++;
        double t = (stars - STOPS[i]) / (STOPS[i + 1] - STOPS[i]);
        int a = COLOURS[i], b = COLOURS[i + 1];
        return String.format("#%02X%02X%02X", mix(a >> 16, b >> 16, t), mix(a >> 8, b >> 8, t), mix(a, b, t));
    }

    private static int mix(int a, int b, double t) {
        double x = Math.pow((a & 0xFF) / 255.0, GAMMA), y = Math.pow((b & 0xFF) / 255.0, GAMMA);
        return (int) Math.round(Math.pow(x + (y - x) * t, 1 / GAMMA) * 255);
    }
}
