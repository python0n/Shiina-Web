package dev.osunolimits.routes.post;

import dev.osunolimits.utils.KnownAccount;
import dev.osunolimits.utils.AccountRules;
import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.XmlConfig;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.captcha.CaptchaProvider;
import dev.osunolimits.modules.geoloc.DefaultGeoLocQuery;
import dev.osunolimits.modules.utils.SessionBuilder;
import dev.osunolimits.modules.utils.UserInfoCache;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.plugins.events.actions.OnRegisterEvent;
import dev.osunolimits.utils.Auth;
import spark.Request;
import spark.Response;

public class HandleRegister extends Shiina {

    public HandleRegister() {
        XmlConfig.getInstance().initKey("shiina.activate.onboarding", "true");
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        // Get form parameters
        String email = req.queryParams("email");
        String username = req.queryParams("username");
        String password = req.queryParams("password");
        String captchaResponse = req.queryParams("cf-turnstile-response");

        // Validate captcha
        if (captchaResponse == null || captchaResponse.isEmpty()) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("register.html", shiina, res, req);
        }

        CaptchaProvider captchaProvider = ShiinaRegistry.getCaptchaProvider();
        if (!captchaProvider.verifyCaptcha(captchaResponse).success) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("register.html", shiina, res, req);
        }

        // Validate form input
        if (email == null || email.isEmpty() || username == null || username.isEmpty() || password == null || password.isEmpty()) {
            shiina.data.put("error", "Missing required fields");
            return renderTemplate("register.html", shiina, res, req);
        }

        String nameError = AccountRules.checkName(shiina.mysql, username, null);
        if (nameError != null) {
            shiina.data.put("error", nameError);
            return renderTemplate("register.html", shiina, res, req);
        }

        // Check if the username or email already exists in the database
        String emailCheckSql = "SELECT `id` FROM `users` WHERE `email` = ?";
        ResultSet emailRs = shiina.mysql.Query(emailCheckSql, email);

        if (emailRs.next()) {
            shiina.data.put("error", "Email already taken");
            return renderTemplate("register.html", shiina, res, req);
        }

        String passwordError = AccountRules.checkPassword(password);
        if (passwordError != null) {
            shiina.data.put("error", passwordError);
            return renderTemplate("register.html", shiina, res, req);
        }

        // Hash as bcrypt(md5(password)) – required for compatibility with the game client (bancho-py-ex)
        String pwBcrypt = Auth.bcryptMd5(password);

        String country = "XX";
        String safeName = AccountRules.safeName(username);
        long curUnixTime = System.currentTimeMillis() / 1000L;

        DefaultGeoLocQuery geoLocQuery = new DefaultGeoLocQuery();
        country = geoLocQuery.getCountryCode(req.ip()).toLowerCase();

        String insertSql = "INSERT INTO `users`(`name`, `safe_name`, `email`, `pw_bcrypt`, `country`, `creation_time`, `latest_activity`) VALUES (?,?,?,?,?,?,?)";
        shiina.mysql.Exec(insertSql, username,safeName, email, pwBcrypt, country, curUnixTime, curUnixTime);

        String userIdSql = "SELECT `id` FROM `users` WHERE `name` = ?";
        ResultSet userIdRs = shiina.mysql.Query(userIdSql, username);
        userIdRs.next();
        int userId = userIdRs.getInt("id");

        for (int i = 0; i <= 8; i++) {
            if (i == 7) continue;
            String insertStatsSql = "INSERT INTO `stats`(`id`, `mode`, `tscore`, `rscore`, `pp`, `plays`, `playtime`, `acc`, `max_combo`, `total_hits`, `replay_views`, `xh_count`, `x_count`, `sh_count`, `s_count`, `a_count`) " +
                                    "VALUES (?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)";
            shiina.mysql.Exec(insertStatsSql, userId, i);
        }

        new OnRegisterEvent(userId, email, country, username, safeName, curUnixTime).callListeners();

        // registered although this browser or network already had an account:
        // note it for the admin (shown under Multi Accounts as a register warning)
        Integer knownId = KnownAccount.fromCookie(req, shiina.mysql);
        if (knownId == null) {
            knownId = KnownAccount.fromIp(req, shiina.mysql);
        }
        if (knownId != null && knownId != userId) {
            shiina.mysql.Exec(
                    "INSERT IGNORE INTO `sh_detections`(`user`, `target`, `detection`, `score`) VALUES (?,?,CURRENT_TIMESTAMP(),0)",
                    userId, knownId);
        }

        UserInfoCache.reloadUser(userId);

        // same flags as HandleLogin without "remember me": 1 day, Secure, HttpOnly
        new SessionBuilder(userId, req).login(res, false);
        KnownAccount.remember(res, userId);

        String refPath = req.queryParams("refPath");
        if (refPath != null && refPath.startsWith("/") && !refPath.startsWith("//") && !refPath.contains("\\")) { // only paths on this site, no redirects elsewhere
            return redirect(res, shiina, refPath);
        }

        boolean isOnBoardingEnabled = Boolean.parseBoolean(XmlConfig.getInstance().get("shiina.activate.onboarding"));

        if(isOnBoardingEnabled) {
            return redirect(res, shiina, "/onboarding");
        }

        return redirect(res, shiina, "/?action=auth");
    }

}
