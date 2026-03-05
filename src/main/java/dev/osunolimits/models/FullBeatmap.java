package dev.osunolimits.models;

import lombok.Data;

@Data
public class FullBeatmap {

    private String md5;
    private int setId;
    private int status;
    private String title;
    private String version;
    private String artist;
    private String creator;
    private String lastUpdate;
    private int totalLength;
    private int maxCombo;
    private int plays;
    private int passes;
    private int mode;

    private double bpm;
    private double cs;
    private double od;
    private double ar;
    private double hp;
    private double diff;

    private MapDiff[] diffs;
    private BeatmapScore[] scores;    

    @Data
    public class BeatmapScore {
        private int id;
        private float pp;
        private long score;
        private String[] mods;
        private String grade;
        private String playTime;
        private int userId;
        private String name;
        private String country;
        private UserInfoObject user;
        private boolean supporter = false;
    }

    @Data
    public class MapDiff {
        private int id;
        private String version;
    }
    
}
