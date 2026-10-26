package dev.osunolimits.routes.post;

import java.sql.ResultSet;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.captcha.CaptchaProvider;
import dev.osunolimits.modules.utils.SessionBuilder;
import dev.osunolimits.modules.utils.UserInfoCache;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.utils.Auth;
import spark.Request;
import spark.Response;

public class HandleRecovery extends Shiina {
    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);
    
        // Get form parameters
        String token = req.queryParams("token");
        String password = req.queryParams("password");
        String captchaResponse = req.queryParams("cf-turnstile-response");

        if(shiina.loggedIn == true) {
            return redirect(res, shiina, "/");
        }
    
        // Validate captcha
        if (captchaResponse == null || captchaResponse.isEmpty()) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("recovery.html", shiina, res, req);
        }
    
        CaptchaProvider captchaProvider = ShiinaRegistry.getCaptchaProvider();
        if (!captchaProvider.verifyCaptcha(captchaResponse).success) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("recovery.html", shiina, res, req);
        }
    
        // Validate form input
        if (token == null || token.isEmpty() || token == null || token.isEmpty() || password == null || password.isEmpty()) {
            shiina.data.put("error", "Missing required fields");
            return renderTemplate("recovery.html", shiina, res, req);
        }
    
        // Validate password strength (between 8-32 characters, at least 3 unique characters)
        if (password.length() < 8 || password.length() > 32 || password.chars().distinct().count() < 3) {
            shiina.data.put("error", "Password must be between 8-32 characters and have at least 3 unique characters");
            return renderTemplate("recovery.html", shiina, res, req);
        }

        String pwMd5 = Auth.md5(password);
        String pwBcrypt = Auth.bcrypt(pwMd5);

        ResultSet recoveryRs = shiina.mysql.Query("SELECT * FROM `sh_recovery` WHERE `token` = ?", token);
        if (!recoveryRs.next()) {
            shiina.data.put("error", "Invalid token");
            return renderTemplate("recovery.html", shiina, res, req);
        }

        int userId = recoveryRs.getInt("user");

        shiina.mysql.Exec("UPDATE `users` SET `pw_bcrypt` = ? WHERE `id` = ?", pwBcrypt, userId);
        shiina.mysql.Exec("DELETE FROM `sh_recovery` WHERE `token` = ?", token);

        UserInfoCache.reloadUser(userId);

        // same flags as HandleLogin without "remember me": 1 day, Secure, HttpOnly
        res.cookie("shiina", new SessionBuilder(userId, req).build(), 604800 / 7, true, true);

        return redirect(res, shiina, "/?recover=success");
    }
    
}
