package dev.osunolimits.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import dev.osunolimits.main.App;
import dev.osunolimits.main.WebServer;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.utils.I18n;
import dev.osunolimits.modules.utils.SEOBuilder;
import lombok.Data;
import spark.Request;
import spark.Response;
import spark.Route;

public class ShiinaDocs {
    public static ArrayList<DocsModel> docs = new ArrayList<>();

    @Data
    public class DocsModel {
        private String filename;
        private String content;
        private String icon;
        private String route;
        private String krz;
        private String navbar;
        private String title;
        private String footer;
        private String behindLogin;
    }

    /**
     * Translated docs: a file whose route is "ROUTE-LANG" (e.g. connect-pl, info-pl) is the
     * LANG version of "ROUTE". /docs/ROUTE shows it to users with that language;
     * ?lang=en (or any code) forces a version, e.g. an "English version" link.
     */
    public static DocsModel localized(DocsModel base, String lang) {
        if (base == null || lang == null || lang.equals(I18n.DEFAULT_LANG)) {
            return base;
        }
        String wanted = base.route + "-" + lang;
        for (DocsModel model : docs) {
            if (model.route.equals(wanted)) {
                return model;
            }
        }
        return base;
    }

    private static boolean isVariant(DocsModel model) {
        int dash = model.route.lastIndexOf('-');
        if (dash <= 0 || !model.route.substring(dash + 1).matches("[a-z]{2}")) {
            return false;
        }
        String baseRoute = model.route.substring(0, dash);
        for (DocsModel other : docs) {
            if (other.route.equals(baseRoute)) {
                return true;
            }
        }
        return false;
    }

    /** Docs for the navbar/footer: one entry per doc, titled in the user's language, linking to the base route. */
    public static List<DocsModel> forLanguage(String lang) {
        List<DocsModel> out = new ArrayList<>();
        for (DocsModel base : docs) {
            if (isVariant(base)) {
                continue;
            }
            DocsModel shown = localized(base, lang);
            if (shown == base) {
                out.add(base);
                continue;
            }
            DocsModel entry = new ShiinaDocs().new DocsModel();
            entry.filename = base.filename;
            entry.content = shown.content;
            entry.icon = base.icon;
            entry.route = base.route;
            entry.krz = shown.krz;
            entry.title = shown.title;
            entry.navbar = base.navbar;
            entry.footer = base.footer;
            entry.behindLogin = base.behindLogin;
            out.add(entry);
        }
        return out;
    }

    public static DocsModel extractComments(String markdown) {
        DocsModel model = new ShiinaDocs().new DocsModel();
        String[] lines = markdown.split("\n");
        if (lines.length == 0 || lines.length < 5)
            return null;
        model.icon = lines[1].substring(5);
        model.route = lines[2].substring(6);
        model.krz = lines[3].substring(6);
        model.title = lines[4].substring(6);
        model.navbar = lines[5].substring(7);
        model.footer = lines[6].substring(7);
        model.behindLogin = lines[7].substring(12);
        return model;
    }

    private String readDocsFile(String fileName) throws IOException {
        List<String> allLines = Files.readAllLines(Paths.get("docs/" + fileName));
        StringBuilder sb = new StringBuilder();
        for (String line : allLines) {
            sb.append(line + "\n");
        }
        return sb.toString();
    }

    public void initializeDocs() {

        File[] files = new File("docs/").listFiles();
        for (File file : files) {

            if (file.isDirectory()) {
                continue;
            }

            if (file.getName().equalsIgnoreCase("README.md") || file.getName().equalsIgnoreCase(".git") || file.getName().equalsIgnoreCase("LICENSE")) {
                continue;
            }

            if (!file.getName().endsWith("md")) {
                App.log.warn("File docs/" + file.getName() + " is not a markdown file");
                continue;
            }

            try {
                String sb = readDocsFile(file.getName());
                DocsModel model = ShiinaDocs.extractComments(sb);
                if (model == null) {
                    App.log.warn("File docs/" + file.getName() + " does not contain comments");
                    continue;
                }
                String[] sbLines = sb.split("\n", -1);
                String sbContent = String.join("\n", java.util.Arrays.copyOfRange(sbLines, 8, sbLines.length));
                java.util.regex.Matcher langMatch = java.util.regex.Pattern.compile("-([a-z]{2})$").matcher(model.route);
                model.content = DocsMarkdown.render(sbContent, langMatch.find() ? langMatch.group(1) : I18n.DEFAULT_LANG);
                model.filename = file.getName();
                docs.add(model);
            } catch (IOException e) {
                App.log.warn("Failed to read file docs/" + file.getName());
            }

        }
        WebServer.get("/docs/:route", getDocsRoute());
    }

    private Route getDocsRoute() {

        return new Shiina() {
            @Override
            public Object handle(Request req, Response res) throws Exception {
                ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

                String route = req.params(":route");
                String lang = (String) shiina.data.get("lang");
                String forced = req.queryParams("lang");
                if (forced != null && I18n.isSupported(forced)) {
                    lang = forced;
                }
                for (DocsModel base : docs) {
                    if (base.route.equals(route)) {
                        DocsModel model = localized(base, lang);
                        if ((base.behindLogin.equals("true") || model.behindLogin.equals("true")) && !shiina.loggedIn) {
                            return redirect(res, shiina, "/login?path=" + req.pathInfo());
                        }

                        shiina.data.put("actNav", 5);
                        shiina.data.put("title", model.title);
                        shiina.data.put("icon", model.icon);
                        shiina.data.put("krz", model.krz);
                        shiina.data.put("content", model.content);
                        shiina.data.put("navbar", model.navbar);
                        shiina.data.put("footer", model.footer);
                        shiina.data.put("behindLogin", model.behindLogin);

                        shiina.data.put("seo",
                                new SEOBuilder(
                                        model.title + " | Docs",
                                        App.customization.get("homeDescription").toString()));

                        return renderTemplate("docs.html", shiina, res, req);
                    }
                }
                return notFound(res, shiina);
            }
        };
    }

}
