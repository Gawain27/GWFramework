package com.gwngames.core.api.asset;

/**
 * Marks an enum as an asset path, providing a path to a concrete asset
 * */
public interface IAssetPath {
        /** default / non-localised path */
        String path();

        /** best matching path for a locale – default impl: fall back */
        default String path(String locale) { return path(); }
}
