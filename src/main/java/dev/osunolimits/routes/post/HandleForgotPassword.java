package dev.osunolimits.routes.post;

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
import dev.osunolimits.modules.utils.Mailer;
import dev.osunolimits.modules.utils.PasswordReset;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.ShiinaRegistry;
import spark.Request;
import spark.Response;

/**
 * Sends a link for setting a new password to the account's e-mail address.
 * The page always answers the same, so it can't be used to check who has an account.
 */
public class HandleForgotPassword extends Shiina {

    public static final String SENT_MESSAGE = "If an account with this e-mail exists, we sent it a link to set a new password. Check the spam folder too. The link is valid for 30 minutes.";

    /** at most this many requests per address per hour */
    private static final int MAX_PER_IP_PER_HOUR = 5;
    /** a new mail for the same account at most this often */
    private static final int ACCOUNT_COOLDOWN_SECONDS = 5 * 60;

    private static final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();

    private static boolean overIpLimit(String ip) {
        long now = System.currentTimeMillis();
        long hourAgo = now - 60 * 60 * 1000L;
        if (requestsByIp.size() > 10000) {
            requestsByIp.clear();
        }
        Deque<Long> times = requestsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < hourAgo) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_IP_PER_HOUR) {
                return true;
            }
            times.addLast(now);
            return false;
        }
    }

    /**
     * The visitor's address. Shiina sits behind nginx, so req.ip() is 127.0.0.1 for
     * everyone and the limit above would be one shared limit for the whole site.
     * X-Real-IP is set by nginx and trusted only when the request came from nginx itself.
     */
    private static String clientIp(Request req) {
        String remote = req.ip();
        if ("127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            String real = req.headers("X-Real-IP");
            if (real != null && !real.isBlank() && real.length() <= 45) {
                return real.trim();
            }
        }
        return remote;
    }

    private Object page(Request req, Response res, ShiinaRequest shiina, String key, String message) throws Exception {
        shiina.data.put(key, message);
        shiina.data.put("seo", new SEOBuilder("Forgot password", App.customization.get("homeDescription").toString()));
        return renderTemplate("forgot.html", shiina, res, req);
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (shiina.loggedIn) {
            return redirect(res, shiina, "/");
        }

        String captchaResponse = req.queryParams("cf-turnstile-response");
        CaptchaProvider captchaProvider = ShiinaRegistry.getCaptchaProvider();
        if (captchaResponse == null || captchaResponse.isEmpty() || !captchaProvider.verifyCaptcha(captchaResponse).success) {
            return page(req, res, shiina, "error", "Invalid Captcha");
        }

        if (!Mailer.isConfigured()) {
            return page(req, res, shiina, "error", "Password reset by e-mail is not available right now");
        }

        String email = req.queryParams("email");
        email = email == null ? "" : email.trim();
        if (email.isEmpty() || email.length() > 254 || !email.contains("@")) {
            return page(req, res, shiina, "error", "Enter the e-mail address of your account");
        }

        if (overIpLimit(clientIp(req))) {
            return page(req, res, shiina, "error", "Too many requests, try again later");
        }

        ResultSet rs = shiina.mysql.Query("SELECT `id`, `name`, `email` FROM `users` WHERE `email` = ? LIMIT 1", email);
        if (rs != null && rs.next()) {
            int userId = rs.getInt("id");
            String name = rs.getString("name");
            String address = rs.getString("email");
            int since = PasswordReset.secondsSinceLast(shiina.mysql, userId);
            if (since < 0 || since >= ACCOUNT_COOLDOWN_SECONDS) {
                String token = PasswordReset.create(shiina.mysql, userId, PasswordReset.EMAIL_TTL_SECONDS);
                String link = App.env.get("DOMAIN") + "/auth/recover?token=" + token;
                Mailer.sendLater(address, "taksiegra - reset hasła / password reset", resetMail(name, link),
                        "password reset, user " + userId);
            }
        }

        return page(req, res, shiina, "info", SENT_MESSAGE);
    }

    private static String resetMail(String name, String link) {
        return "Cześć " + name + ",\n\n"
                + "ktoś (pewnie ty) poprosił o nowe hasło do konta " + name + " na serwerze osu! taksiegra.\n"
                + "Aby ustawić nowe hasło, otwórz ten link (ważny 30 minut):\n\n"
                + link + "\n\n"
                + "Jeśli to nie ty, zignoruj tę wiadomość. Twoje hasło się nie zmieni.\n\n"
                + "----------\n\n"
                + "Hi " + name + ",\n\n"
                + "someone (probably you) asked for a new password for the account " + name + " on the taksiegra osu! server.\n"
                + "To set a new password, open this link (valid for 30 minutes):\n\n"
                + link + "\n\n"
                + "If it wasn't you, ignore this message. Your password stays the same.\n\n"
                + "----------\n"
                + "Prosimy nie odpowiadać na tę wiadomość. / Please do not reply to this message.\n";
    }
}
