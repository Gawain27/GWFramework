package com.gw.editor.util;

import com.gw.editor.template.TemplateDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers to clone templates (whole image or a region).
 */
public class TemplateSlice {
    private TemplateSlice() {
    }

    /**
     * Deep copy that preserves complex/animated state, regions, and all tiles.
     */
    public static TemplateDef copyWhole(TemplateDef src) {
        if (src == null) return null;
        TemplateDef d = new TemplateDef();
        d.id = src.id;
        d.logicalPath = src.logicalPath;
        d.imageWidthPx = src.imageWidthPx;
        d.imageHeightPx = src.imageHeightPx;
        d.tileWidthPx = src.tileWidthPx;
        d.tileHeightPx = src.tileHeightPx;

        // IMPORTANT: keep both flags and all regions for animation
        d.complex = src.complex;
        d.animated = src.animated;

        // deep copy tiles
        d.tiles = new HashMap<>();
        for (Map.Entry<String, TemplateDef.TileDef> e : src.tiles.entrySet()) {
            TemplateDef.TileDef t = e.getValue();
            TemplateDef.TileDef nt = createTile(t);
            d.tiles.put(e.getKey(), nt);
        }

        // deep copy regions (order is the animation order if animated)
        d.regions = new ArrayList<>();
        for (TemplateDef.RegionDef r : src.regions) {
            d.regions.add(new TemplateDef.RegionDef(r.id, r.x0, r.y0, r.x1, r.y1));
        }
        return d;
    }

    public static TemplateDef.TileDef createTile(TemplateDef.TileDef t) {
        TemplateDef.TileDef nt = new TemplateDef.TileDef(t.gx, t.gy);
        nt.tag = t.tag;
        nt.customFloat = t.customFloat;
        nt.gate = t.gate;
        nt.solid = t.solid;
        nt.shape = t.shape;
        nt.orientation = t.orientation;
        return nt;
    }

    /**
     * Copy just a rectangular region in tile coordinates.
     * NOTE: For animated templates you typically want copyWhole() so all frames are preserved.
     */
    public static TemplateDef copyRegion(TemplateDef src, int x0, int y0, int x1, int y1) {
        if (src == null) return null;
        int lx = Math.min(x0, x1), rx = Math.max(x0, x1);
        int ty = Math.min(y0, y1), by = Math.max(y0, y1);

        TemplateDef d = new TemplateDef();
        d.id = src.id;
        d.logicalPath = src.logicalPath;
        d.tileWidthPx = src.tileWidthPx;
        d.tileHeightPx = src.tileHeightPx;

        // pixel size of the cropped texture (used by map draw math)
        d.imageWidthPx = (rx - lx + 1) * d.tileWidthPx;
        d.imageHeightPx = (by - ty + 1) * d.tileHeightPx;

        // keep complex flag; region crop is a single “frame”, so animated = false here
        d.complex = src.complex;
        d.animated = false;

        // copy tiles that fall inside the crop, remapping gx/gy to start at 0
        d.tiles = new HashMap<>();
        for (Map.Entry<String, TemplateDef.TileDef> e : src.tiles.entrySet()) {
            TemplateDef.TileDef t = e.getValue();
            if (t.gx >= lx && t.gx <= rx && t.gy >= ty && t.gy <= by) {
                TemplateDef.TileDef nt = new TemplateDef.TileDef(t.gx - lx, t.gy - ty);
                nt.tag = t.tag;
                nt.customFloat = t.customFloat;
                nt.gate = t.gate;
                nt.solid = t.solid;
                nt.shape = t.shape;
                nt.orientation = t.orientation;
                d.tiles.put((nt.gx) + "," + (nt.gy), nt);
            }
        }

        // regions inside the crop can be normalized (optional); simplest is none for a sliced static texture
        d.regions = List.of();
        return d;
    }
}
