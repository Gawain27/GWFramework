package com.gwngames.core.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;

import java.util.List;

/**
 * Basic String utilities, to avoid making 200 checks every time...
 *
 */
public class StringUtils {
    /**
     * Checks whether a string is considered "empty".
     * A string is considered empty if:
     * - It is null
     * - It is an empty string ""
     * - It contains only whitespace
     * - It is the literal string "null" (case-insensitive)
     * - It contains only invisible characters (e.g., zero-width space, non-breaking space)
     *
     * @param str The string to check
     * @return true if the string is considered empty; false otherwise
     */
    public static boolean isEmpty(String str) {
        if (str == null) return true;

        // Trim and normalize invisible characters
        String normalized = removeInvisibleChars(str).trim();

        // Check for "null" string and zero-length after trimming
        return normalized.isEmpty() || normalized.equalsIgnoreCase("null");
    }

    /**
     * Removes invisible characters from a string.
     * This includes:
     * - Zero-width space (\u200B)
     * - Zero-width non-joiner (\u200C)
     * - Zero-width joiner (\u200D)
     * - Byte Order Mark (BOM, \uFEFF)
     * - Non-breaking space (\u00A0)
     *
     * @param str The input string
     * @return The cleaned string
     */
    public static String removeInvisibleChars(String str) {
        if (str == null) return null;
        return str.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]", "");
    }

    public static String extensionOf(String path) {
        int idx = path.lastIndexOf('.');
        return idx == -1 ? "" : path.substring(idx + 1);
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Escape entities
     */
    public static String escape(Object o) {
        return Parser.unescapeEntities(String.valueOf(o), false);
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    public static boolean startsWithAny(String s, String[] prefixes) {
        for (String p : prefixes) if (s.startsWith(p)) return true;
        return false;
    }

    /**
     * Returns true if the given string is valid (well-formed) HTML according to jsoup's HTML5 parser.
     * Notes:
     * - Treats null/blank as invalid.
     * - Accepts typical HTML5-ish markup; warnings are ignored, only errors fail.
     */
    public static boolean isValidHtml(String html) {
        if (html == null || html.isBlank()) return false;

        try {
            // Configure HTML5 parser and enable error tracking
            Parser parser = Parser.htmlParser();
            parser.setTrackErrors(200); // cap collected errors (adjust if boom)

            // Base URI not needed; pass empty string
            Jsoup.parse(html, "", parser);

            // If the parser recorded any errors, consider it invalid
            List<ParseError> errors = parser.getErrors();
            return errors.isEmpty();
        } catch (Exception ex) {
            // Any unexpected runtime parse problem -> invalid
            return false;
        }
    }
}
