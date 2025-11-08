package com.gw.editor.util;

import com.gw.editor.template.TemplateDef;

import java.util.Map;

/** Utility to create a per-instance snapshot of a TemplateDef (optionally cropped to a region). */
public final class TemplateSlice {
    private TemplateSlice(){}

    /** Whole template copy (shallow for image info, deep for tiles). */
    public static TemplateDef copyWhole(TemplateDef src) {
        TemplateDef dst = new TemplateDef();
        dst.id = src.id;
        dst.logicalPath = src.logicalPath;
        dst.imageWidthPx = src.imageWidthPx;
        dst.imageHeightPx = src.imageHeightPx;
        dst.tileWidthPx = src.tileWidthPx;
        dst.tileHeightPx = src.tileHeightPx;
        dst.complex = false;        // snapshot is always treated “flat” on map
        for (Map.Entry<String, TemplateDef.TileDef> e : src.tiles.entrySet()) {
            TemplateDef.TileDef t = e.getValue();
            TemplateDef.TileDef c = new TemplateDef.TileDef(t.gx, t.gy);
            c.tag = t.tag; c.customFloat = t.customFloat; c.gate = t.gate;
            c.solid = t.solid; c.shape = t.shape; c.orientation = t.orientation;
            dst.tiles.put(e.getKey(), c);
        }
        return dst;
    }

    /**
     * Crop to a region given in TILE coordinates (inclusive x0,y0,x1,y1).
     * Returns a new TemplateDef where tiles are re-keyed starting at (0,0).
     */
    public static TemplateDef copyRegion(TemplateDef src, int x0, int y0, int x1, int y1) {
        int minx = Math.min(x0, x1), miny = Math.min(y0, y1);
        int maxx = Math.max(x0, x1), maxy = Math.max(y0, y1);
        int wTiles = (maxx - minx + 1);
        int hTiles = (maxy - miny + 1);

        TemplateDef dst = new TemplateDef();
        dst.id = src.id;
        dst.logicalPath = src.logicalPath;
        dst.tileWidthPx = src.tileWidthPx;
        dst.tileHeightPx = src.tileHeightPx;
        dst.imageWidthPx  = wTiles * src.tileWidthPx;
        dst.imageHeightPx = hTiles * src.tileHeightPx;
        dst.complex = false;

        for (int gy = miny; gy <= maxy; gy++) {
            for (int gx = minx; gx <= maxx; gx++) {
                TemplateDef.TileDef t = src.tileAt(gx, gy);
                if (t == null) continue;
                int lx = gx - minx, ly = gy - miny; // rebase
                TemplateDef.TileDef c = new TemplateDef.TileDef(lx, ly);
                c.tag = t.tag; c.customFloat = t.customFloat; c.gate = t.gate;
                c.solid = t.solid; c.shape = t.shape; c.orientation = t.orientation;
                dst.tiles.put(lx + "," + ly, c);
            }
        }
        return dst;
    }
}
