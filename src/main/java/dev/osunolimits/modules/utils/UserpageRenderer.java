package dev.osunolimits.modules.utils;

import java.io.IOException;
import java.io.InputStream;

import org.kefirsf.bb.BBProcessorFactory;
import org.kefirsf.bb.ConfigurationFactory;
import org.kefirsf.bb.TextProcessor;
import org.kefirsf.bb.conf.Configuration;
import org.owasp.encoder.Encode;

/**
 * Turns userpage BBCode into HTML with the rules in bbcode.xml (modelled on osu-web).
 * The processor only lets through the HTML written in that file, everything typed by the
 * player is escaped.
 */
public final class UserpageRenderer {

    public static final int MAX_LENGTH = 10000;

    // html saved with this prefix was made by this renderer and its raw column is plain BBCode;
    // older rows keep raw HTML-encoded and are rendered again the first time they are shown
    public static final String MARKER = "<!--bb2-->";

    private static final TextProcessor PROCESSOR = create();

    private UserpageRenderer() {
    }

    private static TextProcessor create() {
        try (InputStream in = UserpageRenderer.class.getResourceAsStream("/bbcode.xml")) {
            if (in == null) {
                throw new IllegalStateException("bbcode.xml is missing");
            }
            Configuration conf = ConfigurationFactory.getInstance().create(in);
            conf.setNestingLimit(50);
            return BBProcessorFactory.getInstance().create(conf);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load bbcode.xml", e);
        }
    }

    public static String render(String bbcode) {
        if (bbcode == null || bbcode.isEmpty()) {
            return MARKER;
        }
        String html = PROCESSOR.process(bbcode).toString();
        if (html.isEmpty()) {
            // too deeply nested: show it as plain text instead of an empty page
            html = Encode.forHtml(bbcode).replace("\n", "<br>");
        }
        return MARKER + html;
    }

    public static boolean isCurrent(String html) {
        return html != null && html.startsWith(MARKER);
    }

    /** raw text of rows saved before this renderer, which stored it HTML-encoded */
    public static String decodeOldRaw(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
