package dev.osunolimits.modules.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

import dev.osunolimits.main.App;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends plain text e-mails through the Brevo API (BREVO_API_KEY, MAIL_FROM and
 * optionally MAIL_FROM_NAME in .config/.env). Mails go out on a background thread,
 * so how long a request takes doesn't tell whether an e-mail was sent.
 * Plain text only: Brevo doesn't rewrite links in it, so reset links aren't tracked.
 */
public final class Mailer {

    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mailer");
        t.setDaemon(true);
        return t;
    });

    private Mailer() {
    }

    private static String env(String key) {
        String value = App.env.get(key);
        return value == null ? "" : value.trim();
    }

    public static boolean isConfigured() {
        return !env("BREVO_API_KEY").isEmpty() && !env("MAIL_FROM").isEmpty();
    }

    /** label is only for the log, e.g. "password reset, user 12" - no addresses in the log */
    public static void sendLater(String to, String subject, String text, String label) {
        if (!isConfigured()) {
            App.log.warn("Mail not sent (" + label + "): BREVO_API_KEY or MAIL_FROM missing");
            return;
        }
        POOL.submit(() -> send(to, subject, text, label));
    }

    private static void send(String to, String subject, String text, String label) {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", env("MAIL_FROM_NAME").isEmpty() ? "taksiegra" : env("MAIL_FROM_NAME"));
        sender.put("email", env("MAIL_FROM"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("textContent", text);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("api-key", env("BREVO_API_KEY"))
                .header("accept", "application/json")
                .post(RequestBody.create(GSON.toJson(body), JSON))
                .build();

        try (Response response = App.sharedClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String reply = response.body() != null ? response.body().string() : "";
                if (reply.length() > 300) {
                    reply = reply.substring(0, 300);
                }
                App.log.warn("Mail not sent (" + label + "): HTTP " + response.code() + " " + reply);
            }
        } catch (Exception e) {
            App.log.warn("Mail not sent (" + label + "): " + e.getMessage());
        }
    }
}
