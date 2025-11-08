package com.gw.editor.template;

import com.google.gson.annotations.SerializedName;

import java.util.*;

public class TemplateDef {
    public String id = "";
    public String logicalPath = "";
    public int imageWidthPx;
    public int imageHeightPx;

    /**
     * Authoring tile size (used to decode regions to pixels).
     */
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;

    /**
     * NEW: marks this template as a complex texture with texture regions.
     */
    public boolean complex = false;

    @SerializedName("tiles")
    public Map<String, TileDef> tiles = new HashMap<>(); // key = "gx,gy"

    @SerializedName("regions")
    public List<RegionDef> regions = new ArrayList<>();

    public TileDef tileAt(int gx, int gy) {
        return tiles.get(key(gx, gy));
    }

    public TileDef ensureTile(int gx, int gy) {
        return tiles.computeIfAbsent(key(gx, gy), k -> new TileDef(gx, gy));
    }

    private static String key(int gx, int gy) {
        return gx + "," + gy;
    }

    public enum ShapeType {RECT_FULL, HALF_RECT, TRIANGLE}

    public enum Orientation {UP, RIGHT, DOWN, LEFT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT}

    public static class TileDef {
        public int gx, gy;
        public String tag;
        public float customFloat;
        public boolean gate;          // NEW: gate tile (green)
        public boolean solid;
        public ShapeType shape;
        public Orientation orientation;

        public TileDef() {
        }

        public TileDef(int gx, int gy) {
            this.gx = gx;
            this.gy = gy;
        }
    }

    public static class RegionDef {
        public String id;
        public int x0, y0;   // inclusive tile coords
        public int x1, y1;   // inclusive tile coords

        public RegionDef() {
        }

        public RegionDef(String id, int x0, int y0, int x1, int y1) {
            this.id = id;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        @Override
        public String toString() {
            return (id == null || id.isBlank() ? "(unnamed)" : id) +
                "  [" + x0 + "," + y0 + " → " + x1 + "," + y1 + "]";
        }
    }

    /**
     * Decode regions to pixel rectangles (x,y,w,h) using current tile size.
     */
    public List<int[]> pixelRegions() {
        if (regions == null || regions.isEmpty()) return List.of();
        List<int[]> out = new ArrayList<>(regions.size());
        for (RegionDef r : regions) {
            int x = Math.min(r.x0, r.x1) * tileWidthPx;
            int y = Math.min(r.y0, r.y1) * tileHeightPx;
            int w = (Math.abs(r.x1 - r.x0) + 1) * tileWidthPx;
            int h = (Math.abs(r.y1 - r.y0) + 1) * tileHeightPx;
            out.add(new int[]{x, y, w, h});
        }
        return out;
    }
}
