package com.gw.editor.template;

import java.util.ArrayList;
import java.util.List;

public final class TemplateDef {
    public String id;
    public String logicalPath;
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;
    public int imageWidthPx;
    public int imageHeightPx;

    public List<TileDef> tiles = new ArrayList<>();

    public static final class TileDef {
        public int gx;
        public int gy;
        public String tag = "";
        public boolean solid = false;
        public float customFloat = 0f;

        public TileDef() {
        }

        public TileDef(int gx, int gy) {
            this.gx = gx;
            this.gy = gy;
        }
    }

    public TileDef tileAt(int gx, int gy) {
        for (TileDef t : tiles) if (t.gx == gx && t.gy == gy) return t;
        return null;
    }

    public TileDef ensureTile(int gx, int gy) {
        TileDef t = tileAt(gx, gy);
        if (t == null) {
            t = new TileDef(gx, gy);
            tiles.add(t);
        }
        return t;
    }
}
