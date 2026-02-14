// *** GENERATED FILE – DO NOT EDIT ***
package com.gwngames.assets;

import java.util.*;
import com.gwngames.core.api.asset.IAssetPath;

public enum GwgameAssets implements IAssetPath {
    ASSETS_TXT("assets.txt"),
    KOALIO_SINGLE_PNG("koalio-single.png"),
    KOALIO_PNG("koalio.png"),
    LEVEL1_TMX("level1.tmx"),
    LIGHTS_ATLAS("lights.atlas"),
    LIGHTS_PNG("lights.png"),
    TILESET_PNG("tileSet.png");

    private final String defaultPath;
    private final Map<String,String> localePaths;

    GwgameAssets(String path) {
        this.defaultPath = path;
        this.localePaths = Map.of();
    }
    GwgameAssets(String baseName, Map<String,String> localePaths) {
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
