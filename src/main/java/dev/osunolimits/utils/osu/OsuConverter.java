package dev.osunolimits.utils.osu;

import java.util.ArrayList;

import dev.osunolimits.utils.Validation;

public class OsuConverter {
    public static String convertStatus(String status) {
        switch (status) {
            // values of maps.status in bancho (RankedStatus)
            case "-1":
                return "Not submitted";
            case "0":
                return "Pending";
            case "1":
                return "Update available";
            case "2":
                return "Ranked";
            case "3":
                return "Approved";
            case "4":
                return "Qualified";
            case "5":
                return "Loved";
            default:
                return "Unknown";
        }
    }
    public static String convertStatusBack(String status) {
        switch (status) {
            case "Not submitted":
                return "-1";
            case "Pending":
                return "0";
            case "Update available":
                return "1";
            case "Ranked":
                return "2";
            case "Approved":
                return "3";
            case "Qualified":
                return "4";
            case "Loved":
                return "5";
            default:
                return "Unknown";
        }
    }

    
    public static String[] convertMods(int mods) {
        ArrayList<String> modList = new ArrayList<>();

        if ((mods & Mods.NoFail.getValue()) > 0) {
            modList.add("NF");
        }
        if ((mods & Mods.Easy.getValue()) > 0) {
            modList.add("EZ");
        }
        if ((mods & Mods.TouchDevice.getValue()) > 0) {
            modList.add("TD");
        }
        if ((mods & Mods.Hidden.getValue()) > 0) {
            modList.add("HD");
        }
        if ((mods & Mods.HardRock.getValue()) > 0) {
            modList.add("HR");
        }
        if ((mods & Mods.SuddenDeath.getValue()) > 0) {
            modList.add("SD");
        }
        if ((mods & Mods.DoubleTime.getValue()) > 0) {
            modList.add("DT");
        }
        if ((mods & Mods.Relax.getValue()) > 0) {
            modList.add("RX");
        }
        if ((mods & Mods.HalfTime.getValue()) > 0) {
            modList.add("HT");
        }
        if ((mods & Mods.Nightcore.getValue()) > 0) {
            modList.add("NC");
        }
        if ((mods & Mods.Flashlight.getValue()) > 0) {
            modList.add("FL");
        }
        if ((mods & Mods.Autoplay.getValue()) > 0) {
            modList.add("AT");
        }
        if ((mods & Mods.SpunOut.getValue()) > 0) {
            modList.add("SO");
        }
        if ((mods & Mods.Relax2.getValue()) > 0) {
            modList.add("AP");
        }
        if ((mods & Mods.Perfect.getValue()) > 0) {
            modList.add("PF");
        }
        if ((mods & Mods.Key4.getValue()) > 0) {
            modList.add("4K");
        }
        if ((mods & Mods.Key5.getValue()) > 0) {
            modList.add("5K");
        }
        if ((mods & Mods.Key6.getValue()) > 0) {
            modList.add("6K");
        }
        if ((mods & Mods.Key7.getValue()) > 0) {
            modList.add("7K");
        }
        if ((mods & Mods.Key8.getValue()) > 0) {
            modList.add("8K");
        }
        if ((mods & Mods.FadeIn.getValue()) > 0) {
            modList.add("FI");
        }
        if ((mods & Mods.Random.getValue()) > 0) {
            modList.add("RD");
        }
        if ((mods & Mods.Cinema.getValue()) > 0) {
            modList.add("CN");
        }
        if ((mods & Mods.Target.getValue()) > 0) {
            modList.add("TP");
        }
        if ((mods & Mods.Key9.getValue()) > 0) {
            modList.add("9K");
        }
        if ((mods & Mods.KeyCoop.getValue()) > 0) {
            modList.add("KC");
        }
        if ((mods & Mods.Key1.getValue()) > 0) {
            modList.add("1K");
        }
        if ((mods & Mods.Key3.getValue()) > 0) {
            modList.add("3K");
        }
        if ((mods & Mods.Key2.getValue()) > 0) {
            modList.add("2K");
        }
        if ((mods & Mods.ScoreV2.getValue()) > 0) {
            modList.add("SV2");
        }
        if ((mods & Mods.Mirror.getValue()) > 0) {
            modList.add("MR");
        }

        // Handle specific conditions
        if (modList.contains("PF") && modList.contains("SD")) {
            modList.remove("SD");
        }
        if (modList.contains("NC") && modList.contains("DT")) {
            modList.remove("DT");
        }

        return modList.toArray(new String[modList.size()]);
    }

    public static String[] modeArray = { "OSU", "OSURX", "OSUAP", "TAIKO", "CATCH", "MANIA", "TAIKORX", "CATCHRX" };

    public static boolean checkForValidMode(String modeStr) {
        if(modeStr == null) return false;
        if(!Validation.isNumeric(modeStr)) return false;

        int mode = Integer.parseInt(modeStr);

        return mode >= 0 && mode <= 8 && mode != 7;
    }

    public static String convertMode(String mode) {
        switch (mode.toUpperCase()) {
            case "OSU":
                return "0";
            case "OSURX":
                return "4";
            case "OSUAP":
                return "8";
            case "TAIKO":
                return "1";
            case "CATCH":
                return "2";
            case "MANIA":
                return "3";
            case "TAIKORX":
                return "5";
            case "CATCHRX":
                return "6";
            default:
                return null;
        }

    }

    public static String convertModeBack(String mode) {
        switch (mode) {
            case "0":
                return "OSU";
            case "4":
                return "OSURX";
            case "8":
                return "OSUAP";
            case "1":
                return "TAIKO";
            case "2":
                return "CATCH";
            case "3":
                return "MANIA";
            case "5":
                return "TAIKORX";
            case "6":
                return "CATCHRX";
            default:
                return null;
        }
    }

    public static String convertModeBackNoRx(String mode) {
        switch (mode) {
            case "0":
                return "OSU";
            case "4":
                return "OSU";
            case "8":
                return "OSU";
            case "1":
                return "TAIKO";
            case "2":
                return "CATCH";
            case "3":
                return "MANIA";
            case "5":
                return "TAIKO";
            case "6":
                return "CATCH";
            default:
                return null;
        }
    }
    public class SortHelper {
        // Available values : tscore, rscore, pp, acc, plays, playtime
        public static String[] sortArray = { "PP", "ACC", "Total Score", "Rated Score", "Plays", "Playtime" };

        public static String convertSort(String sort) {
            switch (sort.toLowerCase()) {
                case "total score":
                    return "tscore";
                case "rated score":
                    return "rscore";
                case "pp":
                    return "pp";
                case "acc":
                    return "acc";
                case "plays":
                    return "plays";
                case "playtime":
                    return "playtime";
                default:
                    return null;
            }
        }

        public static String convertSortBack(String sort) {
            switch (sort.toLowerCase()) {
                case "tscore":
                    return "Total Score";
                case "rscore":
                    return "Ranked Score";
                case "pp":
                    return "PP";
                case "acc":
                    return "ACC";
                case "plays":
                    return "Plays";
                case "playtime":
                    return "Playtime";
                default:
                    return null;
            }
        }
    }

}