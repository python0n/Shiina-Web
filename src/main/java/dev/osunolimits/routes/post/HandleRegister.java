package dev.osunolimits.routes.post;

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
        
        if (!username.matches("^(?! )[\\w\\[\\] -]{2,15}(?<! )$")) {
            shiina.data.put("error", "Username contains invalid characters. Make sure it's 2-15 characters long.");
            return renderTemplate("register.html", shiina, res, req);
        }
    
        // Check if the username or email already exists in the database
        String emailCheckSql = "SELECT `id` FROM `users` WHERE `email` = ?";
        String usernameCheckSql = "SELECT `id` FROM `users` WHERE `name` = ?";
    
        ResultSet emailRs = shiina.mysql.Query(emailCheckSql, email);
        ResultSet usernameRs = shiina.mysql.Query(usernameCheckSql, username);
    
        if (emailRs.next()) {
            shiina.data.put("error", "Email already taken");
            return renderTemplate("register.html", shiina, res, req);
        }
    
        if (usernameRs.next()) {
            shiina.data.put("error", "Username already taken");
            return renderTemplate("register.html", shiina, res, req);
        }
    
        // Validate password strength (between 8-32 characters, at least 3 unique characters)
        if (password.length() < 8 || password.length() > 32 || password.chars().distinct().count() < 3) {
            shiina.data.put("error", "Password must be between 8-32 characters and have at least 3 unique characters");
            return renderTemplate("register.html", shiina, res, req);
        }

        // Hash directly with bcrypt – no MD5 pre-hashing
        String pwBcrypt = Auth.bcrypt(password);

        String country = "XX";
        String safeName = username.toLowerCase().replaceAll(" ", "_");
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

        UserInfoCache.reloadUser(userId);

        res.cookie("shiina", new SessionBuilder(userId, req).build());

        String refPath = req.queryParams("refPath");
        if(refPath != null && !refPath.isEmpty()) {
            return redirect(res, shiina, refPath);
        }

        boolean isOnBoardingEnabled = Boolean.parseBoolean(XmlConfig.getInstance().get("shiina.activate.onboarding"));
        
        if(isOnBoardingEnabled) {
            return redirect(res, shiina, "/onboarding");
        }

        return redirect(res, shiina, "/?action=auth");
    }
    
}
