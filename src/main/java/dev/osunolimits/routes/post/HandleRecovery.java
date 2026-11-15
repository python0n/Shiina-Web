package dev.osunolimits.routes.post;

import java.sql.ResultSet;
import java.util.Map;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.captcha.CaptchaProvider;
import dev.osunolimits.modules.utils.Mailer;
import dev.osunolimits.modules.utils.PasswordReset;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.modules.utils.SessionBuilder;
import dev.osunolimits.modules.utils.UserInfoCache;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.routes.api.chat.NotificationsApi;
import dev.osunolimits.routes.get.simple.Recover;
import dev.osunolimits.utils.AccountRules;
import dev.osunolimits.utils.Auth;
import spark.Request;
import spark.Response;

public class HandleRecovery extends Shiina {

    private Object page(Request req, Response res, ShiinaRequest shiina, String token, boolean tokenValid, String error) throws Exception {
        shiina.data.put("error", error);
        if (tokenValid) {
            shiina.data.put("token", token);
        }
        shiina.data.put("seo", new SEOBuilder("Recover", App.customization.get("homeDescription").toString()));
        return renderTemplate("recover.html", shiina, res, req);
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        String token = req.queryParams("token");
        String password = req.queryParams("password");
        String captchaResponse = req.queryParams("cf-turnstile-response");

        // no "already logged in" check: the link from the e-mail is enough proof,
        // and opening it in a browser that is still logged in must work too

        int userId = PasswordReset.findUser(shiina.mysql, token);
        if (userId <= 0) {
            return page(req, res, shiina, token, false, Recover.EXPIRED_MESSAGE);
        }

        CaptchaProvider captchaProvider = ShiinaRegistry.getCaptchaProvider();
        if (captchaResponse == null || captchaResponse.isEmpty() || !captchaProvider.verifyCaptcha(captchaResponse).success) {
            return page(req, res, shiina, token, true, "Invalid Captcha");
        }

        // the same rules as at registration
        String passwordError = AccountRules.checkPassword(password);
        if (passwordError != null) {
            return page(req, res, shiina, token, true, passwordError);
        }

        String pwBcrypt = Auth.bcrypt(Auth.md5(password));
        shiina.mysql.Exec("UPDATE `users` SET `pw_bcrypt` = ? WHERE `id` = ?", pwBcrypt, userId);

        // the link works only once, and anyone logged in with the old password is logged out
        PasswordReset.consumeAll(shiina.mysql, userId);
        SessionBuilder.deleteAllSessions(userId);
        UserInfoCache.reloadUser(userId);

        NotificationsApi.add(shiina.mysql, userId, "password_changed", Map.of(), "/settings/auth");

        ResultSet rs = shiina.mysql.Query("SELECT `name`, `email` FROM `users` WHERE `id` = ?", userId);
        if (rs != null && rs.next() && Mailer.isConfigured()) {
            Mailer.sendLater(rs.getString("email"), "taksiegra - hasło zmienione / password changed",
                    changedMail(rs.getString("name")), "password changed, user " + userId);
        }

        // same flags as HandleLogin without "remember me": 1 day, Secure, HttpOnly
        new SessionBuilder(userId, req).login(res, false);

        return redirect(res, shiina, "/?recover=success");
    }

    private static String changedMail(String name) {
        return "Cześć " + name + ",\n\n"
                + "hasło do konta " + name + " na serwerze osu! taksiegra zostało właśnie zmienione, a wszystkie inne sesje na stronie zostały wylogowane.\n"
                + "Jeśli to nie ty, od razu napisz do admina (Python-) w grze albo na python@taksiegra.ovh.\n\n"
                + "----------\n\n"
                + "Hi " + name + ",\n\n"
                + "the password of the account " + name + " on the taksiegra osu! server was just changed, and all other sessions on the website were logged out.\n"
                + "If it wasn't you, message the admin (Python-) in game or at python@taksiegra.ovh right away.\n\n"
                + "----------\n"
                + "Prosimy nie odpowiadać na tę wiadomość. / Please do not reply to this message.\n";
    }
}
