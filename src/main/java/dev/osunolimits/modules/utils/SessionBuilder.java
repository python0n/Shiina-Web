package dev.osunolimits.modules.utils;

import java.util.List;

import com.google.gson.Gson;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.geoloc.GeoLocProvider.GeoLocResponse;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.utils.Auth;
import dev.osunolimits.utils.Auth.SessionUser;
import lombok.AllArgsConstructor;
import spark.Request;
import spark.Response;

@AllArgsConstructor
public class SessionBuilder {
    private static final Gson gson = new Gson();

    /** Like osu-web: a remembered login lasts 30 days from the last visit, otherwise 1 day. */
    public static final int REMEMBER_SECONDS = 30 * 24 * 60 * 60;
    public static final int SESSION_SECONDS = 24 * 60 * 60;
    /** How often an active session gets its expiry pushed back. */
    private static final int REFRESH_EVERY_SECONDS = 6 * 60 * 60;
 
    private int id;
    private Request request;

    public String build() {
        return build(false);
    }

    public static int lifetime(boolean remember) {
        return remember ? REMEMBER_SECONDS : SESSION_SECONDS;
    }

    /** Creates the session and sets the "shiina" cookie on the response. */
    public void login(Response res, boolean remember) {
        setCookie(res, build(remember), lifetime(remember));
    }

    /**
     * Writes the session cookie with SameSite=Lax. The cookie API of the web
     * framework has no SameSite parameter, so the header is written by hand.
     * Lax: the cookie is sent when someone opens a link to the site, but not
     * with a form or script request started by another website.
     */
    private static void setCookie(Response res, String token, int maxAge) {
        res.header("Set-Cookie", "shiina=" + token + "; Path=/; Max-Age=" + maxAge
                + "; Secure; HttpOnly; SameSite=Lax");
    }

    /**
     * Sliding expiry: called on every request with a valid session. At most every few hours
     * it extends the session and the cookie, so active users stay logged in.
     */
    public static void refresh(String token, Auth.SessionUser user, Response res) {
        int now = (int) (System.currentTimeMillis() / 1000L);
        int last = user.refreshed != null ? user.refreshed : (user.created != null ? user.created : 0);
        if (now - last < REFRESH_EVERY_SECONDS) {
            return;
        }
        boolean remember = user.remember == null || user.remember;
        user.refreshed = now;
        App.appCache.set("shiina:auth:" + token, gson.toJson(user), lifetime(remember));
        setCookie(res, token, lifetime(remember));
    }

    public String build(boolean remember) {
        String authToken = Auth.generateNewToken();

        SessionUser user = new SessionUser();
        user.id = id;
        user.created = (int) (System.currentTimeMillis() / 1000L);
        user.refreshed = user.created;
        user.remember = remember;
        user.ip = request.ip();
        user.userAgent = request.userAgent() != null ? request.userAgent() : "Unknown";

        GeoLocResponse geoLocResponse = ShiinaRegistry.getGeoLocProvider().getCountryCodeSession(request.ip());
        if(geoLocResponse != null) {
            user.country = geoLocResponse.getCountry();
            user.city = geoLocResponse.getCity();
        } 

        App.appCache.set("shiina:auth:" + authToken, gson.toJson(user), lifetime(remember));
        App.appCache.lpush("shiina:user:" + id + ":tokens", authToken);
        return authToken;
    }

    public static void deleteAllSessions(int userId) {
        List<Object> sessionTokens = App.appCache.lrange("shiina:user:" + userId + ":tokens", 0, -1);
        
        for (Object token : sessionTokens) {
            App.appCache.del("shiina:auth:" + (String) token);
        }
        
        App.appCache.del("shiina:user:" + userId + ":tokens");
    }
}
