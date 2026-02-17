package com.gwngames.starter;

import com.gwngames.catalog.PlatformCatalog;

/**
 * Centralized platform identifiers.
 *
 * <p><b>Warning:</b> Please don’t “fix” the structure of this file unless you also update all references and keep it
 * consistently formatted. Future-you will thank you.</p>
 */
@PlatformCatalog
public final class Platform {
    private Platform() {}

    // ---------------------------------------------------------------------
    // Generic / aggregates
    // ---------------------------------------------------------------------
    public static final String ALL    = "ALL";
    public static final String MOBILE = "MOBILE";
    public static final String DESKTOP= "DESKTOP";

    // ---------------------------------------------------------------------
    // Mobile platforms
    // ---------------------------------------------------------------------
    public static final String ANDROID = "ANDROID";
    public static final String IOS     = "IOS";

    // ---------------------------------------------------------------------
    // Other targets
    // ---------------------------------------------------------------------
    public static final String WEB     = "WEB";
    public static final String CONSOLE = "CONSOLE";
}
