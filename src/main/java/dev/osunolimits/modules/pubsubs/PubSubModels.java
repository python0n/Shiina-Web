package dev.osunolimits.modules.pubsubs;

import lombok.Data;

public class PubSubModels {

    @Data
    public static class RankOutput {
        public int beatmap_id;
        public int status;
        public boolean frozen;
        public int user_id;
        public String user_name;
    }

    @Data
    public static class RestrictInput {
        public int id;
        public int userId; // Admin ID
        public String reason;
    }

    @Data
    public static class UnrestrictInput {
        public int id;
        public int userId; // Admin ID
        public String reason;
    }

    @Data 
    public static class WipeInput {
        public int id;
        public int mode;
    }

    @Data
    public static class AlertAllInput {
        public String message;
    }

    @Data
    public static class GiveDonatorInput {
        public int id;
        public String duration; // Durations: s/h/m/d/w
    }

    @Data
    public static class AddPrivInput {
        public int id;
        public String[] privs; // Privileges: normal, verified, whitelisted, etc.
    }

    @Data
    public static class RemovePrivInput {
        public int id;
        public String[] privs; // Privileges: normal, verified, whitelisted, etc.
    }

    @Data
    public static class CountryChangeInput {
        public int id;
        public String country;
    }

    @Data 
    public static class NameChangeInput {
        public int id;
        public String name;
    }
}
