package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import java.util.regex.Pattern;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

/**
 * Saves (or clears) the Discord username shown on the user's profile,
 * like the Discord field in osu.ppy.sh account settings.
 */
public class HandleDiscordChange extends Shiina {

    // current Discord usernames: 2-32 chars, lowercase letters, digits, "_" and ".", no ".."
    private static final Pattern NEW_USERNAME = Pattern.compile("^(?!.*\\.\\.)[a-z0-9_.]{2,32}$");
    // legacy usernames with a discriminator, e.g. Name#1234
    private static final Pattern LEGACY_USERNAME = Pattern.compile("^[\\p{L}\\p{N} _.\\-]{2,32}#[0-9]{4}$");

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (!value.contains("#")) {
            value = value.toLowerCase();
        }
        return value;
    }

    public static boolean isValid(String value) {
        return NEW_USERNAME.matcher(value).matches() || LEGACY_USERNAME.matcher(value).matches();
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/settings/customization");
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return redirect(res, shiina, "/settings?error=Restricted accounts cannot change their profile");
        }
        int userId = shiina.user.id;
        String discord = normalize(req.queryParams("discord"));

        if (discord.isEmpty()) {
            shiina.mysql.Exec("DELETE FROM `sh_user_socials` WHERE `user_id` = ?", userId);
            return redirect(res, shiina, "/settings/customization?info=Discord username was removed");
        }

        if (!isValid(discord)) {
            return redirect(res, shiina,
                    "/settings/customization?error=Invalid Discord username (2-32 characters: letters, numbers, _ and .)");
        }

        shiina.mysql.Exec(
                "INSERT INTO `sh_user_socials` (`user_id`, `discord`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `discord` = ?",
                userId, discord, discord);

        return redirect(res, shiina, "/settings/customization?info=Discord username was changed");
    }
}
