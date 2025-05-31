package com.gwngames.core.util;

/**
 * Basic String utilities, to avoid making 200 checks every time...
 * */
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
    private static String removeInvisibleChars(String str) {
        if (str == null) return null;
        return str.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]", "");
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
}
