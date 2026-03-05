package dev.osunolimits.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import dev.osunolimits.main.App;
import dev.osunolimits.main.WebServer;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
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
        Parser parser = Parser.builder().build();
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
                Node document = parser.parse(sbContent);
                HtmlRenderer renderer = HtmlRenderer.builder().build();

                model.content = renderer.render(document);
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
                for (DocsModel model : docs) {
                    if (model.route.equals(route)) {
                        if (model.behindLogin.equals("true") && !shiina.loggedIn) {
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
