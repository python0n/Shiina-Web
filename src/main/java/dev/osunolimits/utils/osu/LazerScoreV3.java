package dev.osunolimits.utils.osu;

/**
 * Score v3 = lazer "standardised score" dla osu!std z zapisanych statow.
 * Mnozniki modow wg oficjalnego rebalansu osu!(lazer) z 2026-06 (tsunyoku).
 * Stable submituje tylko domyslne tempa: DT=1.5x -> 1.23, HT=0.75x -> 0.55.
 */
public final class LazerScoreV3 {

    private LazerScoreV3() {}

    private static final int NF = 1, EZ = 2, TD = 4, HD = 8, HR = 16, SD = 32,
                             DT = 64, HT = 256, NC = 512, FL = 1024, SO = 4096, PF = 16384;

    /** Lazerowe mnozniki score (osu!std, po rebalansie 2026-06). */
    public static double modMultiplier(int mods) {
        double m = 1.0;
        if ((mods & EZ) != 0) m *= 0.80;   // Easy
        if ((mods & NF) != 0) m *= 1.00;   // NoFail
        if ((mods & HT) != 0) m *= 0.55;   // HalfTime (0.75x rate)
        if ((mods & HR) != 0) m *= 1.09;   // HardRock
        if ((mods & HD) != 0) m *= 1.04;   // Hidden
        if ((mods & FL) != 0) m *= 1.20;   // Flashlight (domyslne ustawienia)
        if ((mods & SO) != 0) m *= 0.95;   // SpunOut
        if ((mods & TD) != 0) m *= 1.00;   // TouchDevice
        if ((mods & NC) != 0)      m *= 1.23;  // Nightcore (1.5x)
        else if ((mods & DT) != 0) m *= 1.23;  // DoubleTime (1.5x)
        // SD/PF nie zmieniaja mnoznika (1.00)
        return m;
    }

    /** @return lazer Score v3 (0..~1.3M). FC liczy sie wiernie; z missami przyblizenie. */
    public static long compute(int n300, int n100, int n50, int nmiss, int mods) {
        int objects = n300 + n100 + n50 + nmiss;
        if (objects <= 0) return 0L;
        double acc = (300.0 * n300 + 100.0 * n100 + 50.0 * n50) / (300.0 * objects);
        double comboProgress = nmiss == 0 ? 1.0 : 1.0 / Math.sqrt(nmiss + 1.0);
        double base = 500000.0 * acc * comboProgress + 500000.0 * Math.pow(acc, 5) * 1.0;
        return Math.round(base * modMultiplier(mods));
    }
}
