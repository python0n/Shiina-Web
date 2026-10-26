package dev.osunolimits.utils.osu;

/**
 * Accuracy and grade for legacy (osu!stable) scores, ported 1:1 from
 * osu-web's App\Models\Traits\Scoreable (the code osu.ppy.sh uses to show
 * multiplayer match scores). No dependencies, so it can be tested on its own.
 */
public final class LegacyScoreCalc {

    public static final int MODE_OSU = 0;
    public static final int MODE_TAIKO = 1;
    public static final int MODE_FRUITS = 2;
    public static final int MODE_MANIA = 3;

    public static final int SCORING_SCOREV2 = 3;

    public static final int MOD_NOFAIL = 1;
    public static final int MOD_HIDDEN = 8;
    public static final int MOD_FLASHLIGHT = 1024;
    public static final int MOD_FADEIN = 1048576;

    private LegacyScoreCalc() {
    }

    /** Relax/autopilot modes (4-8) map onto their vanilla ruleset. */
    public static int vanillaMode(int mode) {
        return Math.floorMod(mode, 4);
    }

    public static long totalHits(int mode, int scoringType, int n300, int n100, int n50,
            int geki, int katu, int miss) {
        switch (vanillaMode(mode)) {
            case MODE_TAIKO:
                return (long) (n100 + n300 + miss) * 300;
            case MODE_FRUITS:
                return (long) n50 + n100 + n300 + miss + katu;
            case MODE_MANIA:
                return (long) (n50 + n100 + n300 + miss + katu + geki)
                        * (scoringType == SCORING_SCOREV2 ? 305 : 300);
            default:
                return (long) (n50 + n100 + n300 + miss) * 300;
        }
    }

    public static long hits(int mode, int scoringType, int n300, int n100, int n50,
            int geki, int katu, int miss) {
        switch (vanillaMode(mode)) {
            case MODE_TAIKO:
                return (long) n100 * 150 + (long) n300 * 300;
            case MODE_FRUITS:
                return (long) n50 + n100 + n300;
            case MODE_MANIA:
                if (scoringType == SCORING_SCOREV2) {
                    return (long) n50 * 50 + (long) n100 * 100 + (long) katu * 200
                            + (long) n300 * 300 + (long) geki * 305;
                }
                return (long) n50 * 50 + (long) n100 * 100 + (long) katu * 200
                        + (long) (n300 + geki) * 300;
            default:
                return (long) n50 * 50 + (long) n100 * 100 + (long) n300 * 300;
        }
    }

    /** Accuracy in the range 0..1 (osu-web returns 1 for a score with no hits). */
    public static double accuracy(int mode, int scoringType, int n300, int n100, int n50,
            int geki, int katu, int miss) {
        long total = totalHits(mode, scoringType, n300, n100, n50, geki, katu, miss);
        if (total == 0) {
            return 1.0;
        }
        return (double) hits(mode, scoringType, n300, n100, n50, geki, katu, miss) / total;
    }

    /**
     * Official rule (osu! API get_match): a score is failed only if the player
     * was failed at the end of the map; No Fail can never fail.
     */
    public static boolean effectivePass(boolean storedPassed, int mods) {
        return storedPassed || (mods & MOD_NOFAIL) != 0;
    }

    public static String grade(int mode, int scoringType, int n300, int n100, int n50,
            int geki, int katu, int miss, boolean passed, int mods) {
        if (!passed) {
            return "F";
        }

        boolean hidden = (mods & (MOD_HIDDEN | MOD_FLASHLIGHT | MOD_FADEIN)) != 0;
        long totalHits = totalHits(mode, scoringType, n300, n100, n50, geki, katu, miss);
        double accuracy = accuracy(mode, scoringType, n300, n100, n50, geki, katu, miss);

        switch (vanillaMode(mode)) {
            case MODE_OSU:
            case MODE_TAIKO: {
                double totalHitCount = totalHits / 300.0;
                double ratio300 = totalHits == 0 ? 1 : n300 / totalHitCount;
                double ratio50 = totalHits == 0 ? 1 : n50 / totalHitCount;
                boolean sOk = vanillaMode(mode) == MODE_OSU ? ratio50 <= 0.01 : ratio50 < 0.01;

                if (ratio300 == 1.0) {
                    return hidden ? "XH" : "X";
                } else if (ratio300 > 0.9 && sOk && miss == 0) {
                    return hidden ? "SH" : "S";
                } else if ((ratio300 > 0.8 && miss == 0) || ratio300 > 0.9) {
                    return "A";
                } else if ((ratio300 > 0.7 && miss == 0) || ratio300 > 0.8) {
                    return "B";
                } else if (ratio300 > 0.6) {
                    return "C";
                }
                return "D";
            }
            case MODE_FRUITS:
                if (accuracy == 1.0) {
                    return hidden ? "XH" : "X";
                } else if (accuracy > 0.98) {
                    return hidden ? "SH" : "S";
                } else if (accuracy > 0.94) {
                    return "A";
                } else if (accuracy > 0.9) {
                    return "B";
                } else if (accuracy > 0.85) {
                    return "C";
                }
                return "D";
            default: // mania
                if (accuracy == 1.0) {
                    return hidden ? "XH" : "X";
                } else if (accuracy > 0.95) {
                    return hidden ? "SH" : "S";
                } else if (accuracy > 0.9) {
                    return "A";
                } else if (accuracy > 0.8) {
                    return "B";
                } else if (accuracy > 0.7) {
                    return "C";
                }
                return "D";
        }
    }
}
