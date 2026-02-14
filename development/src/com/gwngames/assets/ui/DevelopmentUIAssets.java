// *** GENERATED FILE – DO NOT EDIT ***
package com.gwngames.assets.ui;

import java.util.*;
import com.gwngames.core.api.asset.IAssetPath;

public enum DevelopmentUIAssets implements IAssetPath {
    FONT_LIST_FNT("ui/font-list.fnt"),
    FONT_SUBTITLE_FNT("ui/font-subtitle.fnt"),
    FONT_WINDOW_FNT("ui/font-window.fnt"),
    FONT_FNT("ui/font.fnt"),
    UISKIN_ATLAS("ui/uiskin.atlas"),
    UISKIN_JSON("ui/uiskin.json"),
    UISKIN_PNG("ui/uiskin.png");

    private final String defaultPath;
    private final Map<String,String> localePaths;

    DevelopmentUIAssets(String path) {
        this.defaultPath = path;
        this.localePaths = Map.of();
    }
    DevelopmentUIAssets(String baseName, Map<String,String> localePaths) {
        this.defaultPath = baseName;
        this.localePaths = Collections.unmodifiableMap(localePaths);
    }

    /** path for the default (non-localised) resource */
    public String path() { return defaultPath; }

    /** path for a specific locale or fall back to default */
    public String path(String locale) {
        return localePaths.getOrDefault(locale, defaultPath);
    }

    /** list of locales explicitly provided for this asset */
    public List<String> locales() {
        return List.copyOf(localePaths.keySet());
    }

    @Override public String toString() { return path(); }
}
