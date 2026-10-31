package dev.osunolimits.utils;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import dev.osunolimits.common.MySQL;
import dev.osunolimits.main.App;

/**
 * Name and password rules, the same as bancho uses for in-game registration,
 * so an account made or renamed on the website is always valid in the game.
 * DISALLOWED_NAMES / DISALLOWED_PASSWORDS can be copied from bancho's .env
 * into Shiina's .config/.env (comma separated).
 */
public final class AccountRules {

    public static final String NAME_PATTERN = "^(?! )[\\w\\[\\] -]{2,15}(?<! )$";

    private AccountRules() {}

    /** bancho: name.lower().replace(" ", "_") */
    public static String safeName(String name) {
        return name.toLowerCase(Locale.ROOT).replace(" ", "_");
    }

    private static Set<String> listFromEnv(String key) {
        String raw = App.env.get(key);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Returns an error message (English, translated through msg.* keys), or null
     * when the name can be used. {@code ownUserId} is the player renaming
     * themselves (may keep their own name with different capitalisation), or null.
     */
    public static String checkName(MySQL mysql, String name, Integer ownUserId) throws Exception {
        if (name == null || !name.matches(NAME_PATTERN)) {
            return "Name must be 2-15 characters: letters, numbers, spaces, _ - [ ]";
        }
        if (name.contains("_") && name.contains(" ")) {
            return "Name may contain _ or spaces, but not both";
        }
        String safe = safeName(name);
        Set<String> disallowed = listFromEnv("DISALLOWED_NAMES");
        if (disallowed.contains(name.toLowerCase(Locale.ROOT)) || disallowed.contains(safe)) {
            return "This name is not allowed";
        }
        try (ResultSet rs = mysql.Query("SELECT `id` FROM `users` WHERE `safe_name` = ?", safe)) {
            while (rs != null && rs.next()) {
                if (ownUserId == null || rs.getInt("id") != ownUserId) {
                    return "Name already taken";
                }
            }
        }
        return null;
    }

    /** Returns an error message, or null when the password is fine. */
    public static String checkPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 32) {
            return "Password must be 8-32 characters long";
        }
        if (password.chars().distinct().count() < 4) {
            return "Password must have at least 4 different characters";
        }
        if (listFromEnv("DISALLOWED_PASSWORDS").contains(password.toLowerCase(Locale.ROOT))) {
            return "This password is too simple";
        }
        return null;
    }
}
