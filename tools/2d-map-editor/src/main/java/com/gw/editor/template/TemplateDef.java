package com.gw.editor.template;

import com.google.gson.annotations.SerializedName;

import java.util.*;

public class TemplateDef {
    public String id = "";
    public String logicalPath = "";
    public int imageWidthPx;
    public int imageHeightPx;

    // Tile grid size used when authoring
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;

    @SerializedName("tiles")
    public Map<String, TileDef> tiles = new HashMap<>(); // key = gx+","+gy

    @SerializedName("regions")
    public List<RegionDef> regions = new ArrayList<>();

    public TileDef tileAt(int gx, int gy) {
        return tiles.get(key(gx, gy));
    }

    public TileDef ensureTile(int gx, int gy) {
        return tiles.computeIfAbsent(key(gx, gy), k -> new TileDef(gx, gy));
    }

    private static String key(int gx, int gy) { return gx + "," + gy; }

    public enum ShapeType { RECT_FULL, HALF_RECT, TRIANGLE }
    public enum Orientation { UP, RIGHT, DOWN, LEFT }

    public static class TileDef {
        public int gx, gy;

        // editable properties
        public String tag;
        public float customFloat;
        public boolean solid;
        public ShapeType shape;
        public Orientation orientation;

        // NEW: gate tile
        public boolean gate;

        public TileDef() {}
        public TileDef(int gx, int gy) { this.gx = gx; this.gy = gy; }
    }

    public static class RegionDef {
        public String id;    // user-assigned
        public int x0, y0;   // inclusive top-left (grid coords)
        public int x1, y1;   // inclusive bottom-right (grid coords)

        public RegionDef() {}
        public RegionDef(String id, int x0, int y0, int x1, int y1) {
            this.id = id; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }

        @Override public String toString() {
            return (id == null || id.isBlank() ? "(unnamed)" : id) +
                "  [" + x0 + "," + y0 + " → " + x1 + "," + y1 + "]";
        }
    }
}
