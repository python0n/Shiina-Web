package dev.osunolimits.common;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import dev.osunolimits.main.App;
import okhttp3.Request;

public class APIRequest {
    private static final Logger LOG = (Logger) LoggerFactory.getLogger("APIRequest");

    public static Request build(String url) {
        if (Boolean.parseBoolean(App.loggerEnv.get("API_LOG")))
            LOG.info("| Bancho.py | API Request | " + App.env.get("APIURL") + url);
        Request.Builder builder = new Request.Builder().url(App.env.get("APIURL") + url);
        // same secret as INTERNAL_API_KEY in bancho's .env: lets Shiina see restricted
        // players in the api (their own profile, staff), the public api hides them
        String internalKey = App.env.get("INTERNAL_API_KEY");
        if (internalKey != null && !internalKey.isBlank()) {
            builder.header("X-Internal-Key", internalKey);
        }
        return builder.build();
    }
}
