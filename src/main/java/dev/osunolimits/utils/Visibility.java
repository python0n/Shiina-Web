package dev.osunolimits.utils;

import java.sql.ResultSet;

import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;

/**
 * Restricted players are hidden from everyone except themselves and staff,
 * like on osu!. One place for that rule, so every page applies it the same way.
 */
public final class Visibility {

    private static final int UNRESTRICTED = 1;
    private static final int STAFF = (1 << 12) | (1 << 13) | (1 << 14); // moderator, admin, developer

    private Visibility() {}

    public static boolean isRestricted(int priv) {
        return (priv & UNRESTRICTED) == 0;
    }

    public static boolean isStaff(ShiinaRequest shiina) {
        return shiina.user != null && shiina.user.priv != null && (shiina.user.priv & STAFF) != 0;
    }

    /** Whether the viewer of this request may see the given player. */
    public static boolean canSee(ShiinaRequest shiina, int targetUserId, int targetPriv) {
        if (!isRestricted(targetPriv)) {
            return true;
        }
        if (shiina.user != null && shiina.user.id != null && shiina.user.id == targetUserId) {
            return true;
        }
        return isStaff(shiina);
    }

    /** Same as {@link #canSee(ShiinaRequest, int, int)}, reading the player's privileges from the database. */
    public static boolean canSee(ShiinaRequest shiina, int targetUserId) {
        try (ResultSet rs = shiina.mysql.Query("SELECT `priv` FROM `users` WHERE `id` = ?", targetUserId)) {
            if (rs == null || !rs.next()) {
                return false;
            }
            return canSee(shiina, targetUserId, rs.getInt("priv"));
        } catch (Exception e) {
            return false;
        }
    }

    /** SQL condition for queries joining `users` as {@code alias}: hides restricted players except the viewer. */
    public static String sqlVisible(ShiinaRequest shiina, String alias) {
        if (isStaff(shiina)) {
            return "1 = 1";
        }
        int viewerId = shiina.user != null && shiina.user.id != null ? shiina.user.id : -1;
        return "(" + alias + ".`priv` & 1 OR " + alias + ".`id` = " + viewerId + ")";
    }

}
