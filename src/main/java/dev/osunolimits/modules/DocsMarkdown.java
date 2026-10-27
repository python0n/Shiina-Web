package dev.osunolimits.modules;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Markdown to HTML for docs/*.md: GitHub-style tables, autolinks, strikethrough,
 * task lists, footnotes, heading ids (for the table of contents) and alerts
 * written as a quote starting with [!NOTE], [!TIP], [!IMPORTANT], [!WARNING] or [!CAUTION],
 * optionally followed by a custom title on the same line.
 */
public final class DocsMarkdown {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            FootnotesExtension.create(),
            HeadingAnchorExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    private static final Pattern ALERT = Pattern.compile(
            "<blockquote>\\n<p>\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\][ \\t]*([^\\n<]*)(\\n|</p>\\n?)");

    private static final Map<String, String> TITLES_EN = Map.of(
            "NOTE", "Note", "TIP", "Tip", "IMPORTANT", "Important", "WARNING", "Warning", "CAUTION", "Caution");
    private static final Map<String, String> TITLES_PL = Map.of(
            "NOTE", "Uwaga", "TIP", "Wskazówka", "IMPORTANT", "Ważne", "WARNING", "Ostrzeżenie", "CAUTION", "Ostrożnie");

    private DocsMarkdown() {
    }

    /** Renders a doc body; lang picks the default alert titles (pl or anything else for English). */
    public static String render(String markdown, String lang) {
        Node document = PARSER.parse(markdown);
        String html = RENDERER.render(document);
        html = alerts(html, "pl".equals(lang) ? TITLES_PL : TITLES_EN);
        html = html.replace("<table>", "<div class=\"doc-table\"><table class=\"table table-sm\">")
                .replace("</table>", "</table></div>");
        return html;
    }

    private static String alerts(String html, Map<String, String> titles) {
        Matcher m = ALERT.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String kind = m.group(1);
            String title = m.group(2).isBlank() ? titles.get(kind) : m.group(2).trim();
            boolean ownParagraph = m.group(3).startsWith("</p>");
            String replacement = "<blockquote class=\"doc-alert doc-alert-" + kind.toLowerCase() + "\">\n"
                    + "<p class=\"doc-alert-title\">" + title + "</p>\n"
                    + (ownParagraph ? "" : "<p>");
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
