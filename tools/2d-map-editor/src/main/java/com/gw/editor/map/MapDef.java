package com.gw.editor.map;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * A tile-based map in tile units; origin is (0,0).
 */
public class MapDef {
    public String id = "";
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;

    /**
     * Map dimensions in tiles (width = columns, height = rows).
     */
    public int widthTiles = 64;
    public int heightTiles = 36;

    /**
     * Optional author notes / metadata
     */
    public String notes;

    @SerializedName("placements")
    public List<Placement> placements = new ArrayList<>();

    /**
     * A placed template (or a region of a complex template).
     */
    public static class Placement {
        public String templateId;      // id of TemplateDef
        public int regionIndex = -1;   // -1 = whole template; >=0 = region index
        public int gx, gy;             // map grid coords (tile units)
        public int wTiles, hTiles;     // footprint size in tiles

        public Placement() {
        }

        public Placement(String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles) {
            this.templateId = templateId;
            this.regionIndex = regionIndex;
            this.gx = gx;
            this.gy = gy;
            this.wTiles = wTiles;
            this.hTiles = hTiles;
        }
    }
}
