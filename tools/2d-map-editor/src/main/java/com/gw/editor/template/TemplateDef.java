package com.gw.editor.template;

import java.util.ArrayList;
import java.util.List;

/** JSON DTO for a single texture template. */
public class TemplateDef {
    /** Human-readable id for your template. */
    public String id;

    /** Asset enum info (so you can re-resolve via IAssetPath in runtime). */
    public String assetEnumClass;  // e.g. "com.gwngames.game.assets.Textures"
    public String assetEnumName;   // e.g. "CRATE_WOOD"

    /** Raw path fallback (useful in editor). Optional but handy. */
    public String logicalPath;     // e.g. "textures/crate_wood.png"

    /** Tile size IN PIXELS (editor-time units). */
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;

    /** Image pixel size cached by editor for convenience (not strictly required). */
    public int imageWidthPx;
    public int imageHeightPx;

    /** Tiles placeholder—we’ll define properties later. */
    public List<TileDef> tiles = new ArrayList<>();

    /** Represents one grid cell; properties TBD. */
    public static class TileDef {
        public int gx; // grid x
        public int gy; // grid y
        // Future properties go here (shape, metadata, flags, etc.)
    }
}
