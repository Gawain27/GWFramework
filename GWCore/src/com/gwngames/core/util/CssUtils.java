package com.gwngames.core.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Minimal CSS helper built on {@link FileUtils}.
 *
 * <p><strong>Goals</strong></p>
 * <ul>
 *   <li>Fast loading of large style-sheets</li>
 *   <li>Simple parse: selector → declarations (string)</li>
 *   <li>No external CSS parser dependency</li>
 * </ul>
 *
 * <p>Not a full CSS spec implementation – good enough for dashboard skinning
 * or server-side manipulation.</p>
 */
public final class CssUtils {

    /**
     * Reads a CSS file and returns a map
     * <pre>{@code
     * ".card"      -> "background:#111; border-radius:8px"
     * "h2, h3"     -> "color:#0af"
     * }</pre>
     *
     * @param cssFile path to the style-sheet
     */
    public static Map<String, String> parseFile(Path cssFile) throws IOException {
        String raw = FileUtils.readUtf8(cssFile);
        return parse(raw);
    }

    /**
     * Basic CSS parser:
     * <ol>
     *   <li>removes comments <code>/* … *&#47;</code></li>
     *   <li>finds <code>selector { body }</code> pairs</li>
     * </ol>
     *
     * @return immutable map selector → declarations
     */
    public static Map<String, String> parse(String css) {
        Map<String, String> out = new LinkedHashMap<>();
        // 1. kill comments
        css = css.replaceAll("/\\*.*?\\*/", "");
        // 2. split by closing brace
        for (String block : css.split("}")) {
            int i = block.indexOf('{');
            if (i < 0) continue;
            String sel  = block.substring(0, i).trim();
            String body = block.substring(i + 1).trim().replaceAll("\\s+", " ");
            if (!sel.isEmpty() && !body.isEmpty())
                out.put(sel, body);
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Quickly checks if the sheet already contains a selector.
     */
    public static boolean hasSelector(Path cssFile, String selector) throws IOException {
        try (var lines = FileUtils.lines(cssFile)) {
            return lines.anyMatch(l -> l.contains(selector));
        }
    }

    private CssUtils() { }
}
