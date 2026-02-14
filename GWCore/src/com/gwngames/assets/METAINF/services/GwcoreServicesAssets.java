// *** GENERATED FILE – DO NOT EDIT ***
package com.gwngames.assets.METAINF.services;

import java.util.*;
import com.gwngames.core.api.asset.IAssetPath;

public enum GwcoreServicesAssets implements IAssetPath {
    ORG_SLF4J_SPI_SLF4JSERVICEPROVIDER("META-INF/services/org.slf4j.spi.SLF4JServiceProvider");

    private final String defaultPath;
    private final Map<String,String> localePaths;

    GwcoreServicesAssets(String path) {
        this.defaultPath = path;
        this.localePaths = Map.of();
    }
    GwcoreServicesAssets(String baseName, Map<String,String> localePaths) {
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
