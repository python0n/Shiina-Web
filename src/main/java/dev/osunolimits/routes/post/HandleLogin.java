package dev.osunolimits.routes.post;

import dev.osunolimits.utils.KnownAccount;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.osunolimits.main.App;
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

public class HandleLogin extends Shiina {

    /**
     * Wrong passwords are counted per (account, address), like in bancho: one address
     * can't keep guessing, and nobody else can lock an account by failing on purpose.
     */
    private static final int MAX_FAILS = 10;
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final Map<String, Deque<Long>> fails = new ConcurrentHashMap<>();

    private static String failKey(String account, Request req) {
        return account.toLowerCase() + "|" + dev.osunolimits.utils.ClientIp.of(req);
    }

    private static boolean tooManyFails(String key) {
        Deque<Long> times = fails.get(key);
        if (times == null) {
            return false;
        }
        synchronized (times) {
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.size() >= MAX_FAILS;
        }
    }

    private static void recordFail(String key) {
        if (fails.size() > 20000) {
            fails.clear();
        }
        Deque<Long> times = fails.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(System.currentTimeMillis());
        }
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String captchaResponse = req.queryParams("cf-turnstile-response");

        if (req.cookie("shiina") != null) {
            if (App.appCache.get("shiina:auth:" + req.cookie("shiina")) != null) {
                shiina.data.put("info", "You are already logged in");
                return renderTemplate("login.html", shiina, res, req);
            }
        }

        if (captchaResponse == null || captchaResponse.isEmpty()) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("login.html", shiina, res, req);
        }

        String input = req.queryParams("input");

        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        if (input == null || input.isEmpty()) {
            shiina.data.put("error", "Invalid (Username/Mail) or Password");
            return renderTemplate("login.html", shiina, res, req);
        }

        String password = req.queryParams("password");
        if (password == null || password.isEmpty()) {
            shiina.data.put("error", "Invalid (Username/Mail) or Password");
            return renderTemplate("login.html", shiina, res, req);
        }
        boolean rememberMe = false;
        if (req.queryParams("remember") != null) {
            rememberMe = true;
        }

        String failKey = failKey(input, req);
        if (tooManyFails(failKey)) {
            shiina.data.put("error", "Too many failed logins, try again in 15 minutes");
            return renderTemplate("login.html", shiina, res, req);
        }

        CaptchaProvider captchaProvider = ShiinaRegistry.getCaptchaProvider();
        if (!captchaProvider.verifyCaptcha(captchaResponse).success) {
            shiina.data.put("error", "Invalid Captcha");
            return renderTemplate("login.html", shiina, res, req);
        }

        String selectSql = "";
        String mailSql = "SELECT `safe_name`, `pw_bcrypt`, `name`, `id`, `priv`, `email` FROM `users` WHERE `email` = ?";
        String nameSql = "SELECT `safe_name`, `pw_bcrypt`, `name`, `id`, `priv`, `email` FROM `users` WHERE `name` = ?";

        if (input.matches(emailRegex)) {
            selectSql = mailSql;
        } else {
            selectSql = nameSql;
        }

        ResultSet validationRs = shiina.mysql.Query(selectSql, input);
        if (validationRs == null || !validationRs.next()) {
            recordFail(failKey);
            shiina.data.put("error", "Invalid (Username/Mail) or Password");
            return renderTemplate("login.html", shiina, res, req);
        }
        String pwBcrypt = validationRs.getString("pw_bcrypt");
        int userId = validationRs.getInt("id");

        Auth.PwCheckResult pwResult = Auth.checkPwLazy(password, pwBcrypt);
        if (!pwResult.matched) {
            recordFail(failKey);
            shiina.data.put("error", "Invalid (Username/Mail) or Password");
            return renderTemplate("login.html", shiina, res, req);
        }

        fails.remove(failKey);

        if (pwResult.needsRehash) {
            shiina.mysql.Exec("UPDATE `users` SET `pw_bcrypt` = ? WHERE `id` = ?", pwResult.newHash, userId);
        }

        UserInfoCache.reloadUserIfNotPresent(userId);

        new SessionBuilder(userId, req).login(res, rememberMe);
        KnownAccount.remember(res, userId);

        ResultSet dbPrivData = shiina.mysql.Query("SELECT priv FROM users WHERE id = ?", userId);
        int realPriv = 0;
        if (dbPrivData.next()) {
            realPriv = dbPrivData.getInt("priv");
        }

        if (realPriv == 1) {
            return redirect(res, shiina, "/");
        }

        String refPath = req.queryParams("refPath");
        if (refPath != null && refPath.startsWith("/") && !refPath.startsWith("//") && !refPath.contains("\\")) { // only paths on this site, no redirects elsewhere
            return redirect(res, shiina, refPath);
        }

        return redirect(res, shiina, "/?action=auth");
    }

}
