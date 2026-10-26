package dev.osunolimits.routes.get.settings;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.XmlConfig;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.SEOBuilder;
import dev.osunolimits.plugins.NavbarRegister;
import dev.osunolimits.utils.osu.OsuConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import spark.Request;
import spark.Response;

public class Customization extends Shiina {

    public Customization() {
        // Remove of old key TODO: move to migrationss
        XmlConfig.getInstance().remove("clans.create.for.supporter");

        XmlConfig.getInstance().initKey("shiina.clans.create-supporter", "true");
        XmlConfig.getInstance().initKey("donator.gif-support", "false");
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 100);

        if(!shiina.loggedIn) {
            res.redirect("/login");
            return notFound(res, shiina);
        }

        if(req.queryParams("info") != null) {
            shiina.data.put("info", req.queryParams("info"));
        }

        if(req.queryParams("error") != null) {
            shiina.data.put("error", req.queryParams("error"));
        }

        ResultSet settingsResult = shiina.mysql.Query("SELECT `country`, `raw`, `preferred_mode`, `clan_id` FROM `users` LEFT JOIN `userpages` ON `users`.`id` = `userpages`.`user_id` WHERE `users`.`id` = ?;", shiina.user.id);
        if(!settingsResult.next()) {
            return notFound(res, shiina);
        }

        ArrayList<DataMode> modes = new ArrayList<>();
        for (String mode : OsuConverter.modeArray) {
            DataMode dataMode = new DataMode(mode, Integer.parseInt(OsuConverter.convertMode(mode)));
            modes.add(dataMode);
        }

        String[] countryCodes = Locale.getISOCountries();
        List<DataCountry> countries = new ArrayList<>();
        for (String countryCode : countryCodes) {
            Locale locale = Locale.of("", countryCode); 
            DataCountry country = new DataCountry();
            country.setCode(countryCode);
            country.setName(locale.getDisplayCountry());
            countries.add(country);
        }

        shiina.data.put("curCountry", settingsResult.getString("country"));
        shiina.data.put("curUserpage", settingsResult.getString("raw"));
        shiina.data.put("curMode", settingsResult.getInt("preferred_mode"));
        shiina.data.put("curClanId", settingsResult.getInt("clan_id"));

        ResultSet discordRs = shiina.mysql.Query("SELECT `discord` FROM `sh_user_socials` WHERE `user_id` = ?", shiina.user.id);
        shiina.data.put("curDiscord", discordRs != null && discordRs.next() && discordRs.getString("discord") != null ? discordRs.getString("discord") : "");

        shiina.data.put("modes", modes);
        shiina.data.put("countries", countries);
        shiina.data.put("clanCreateSupporter", XmlConfig.getInstance().get("shiina.clans.create-supporter").equals("true"));

        shiina.data.put("gifSupport", Boolean.parseBoolean(XmlConfig.getInstance().get("donator.gif-support")));

        shiina.data.put("pluginNav", NavbarRegister.getSettingsItems());
        shiina.data.put("seo", new SEOBuilder("Settings | Customization", App.customization.get("homeDescription").toString()));
        return renderTemplate("settings/customization.html", shiina, res, req);
    }

    @Data
    @AllArgsConstructor
    public class DataMode {
        private String name;
        private int id;
    }
    

    @Data
    public class DataCountry {
        private String code;
        private String name;
    }
}
