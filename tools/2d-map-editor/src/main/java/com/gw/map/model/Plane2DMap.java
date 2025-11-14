package com.gw.map.model;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateSlice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Plane2DMap {

    public final List<Integer> layers = new ArrayList<>(List.of(0, 1, 2));
    public final List<Placement> placements = new ArrayList<>();
    public final List<GateMeta> gateMetas = new ArrayList<>();
    public final List<GateLink> gateLinks = new ArrayList<>();
    public String id = UUID.randomUUID().toString();
    public Base base = Base.Z;
    public int planeIndex = 0;
    public int widthTiles = 16;
    public int heightTiles = 16;

    /**
     * Build a new empty plane with known sizes.
     */
    public static Plane2DMap fromSize(SelectionState.BasePlane sbase, int index, int w, int h) {
        Plane2DMap pm = new Plane2DMap();
        pm.base = switch (sbase) {
            case X -> Base.X;
            case Y -> Base.Y;
            case Z -> Base.Z;
        };
        pm.planeIndex = index;
        pm.widthTiles = Math.max(1, w);
        pm.heightTiles = Math.max(1, h);
        return pm;
    }

    public void normalizeLayers() {
        if (layers.isEmpty()) layers.add(0);
        for (Placement p : placements) {
            p.layer = Math.max(0, Math.min(p.layer, layers.size() - 1));
        }
    }

    public Optional<GateMeta> findGateMeta(GateRef ref) {
        return gateMetas.stream().filter(m -> m.ref.equals(ref)).findFirst();
    }

    public GateMeta ensureGateMeta(GateRef ref) {
        return findGateMeta(ref).orElseGet(() -> {
            var m = new GateMeta(ref);
            gateMetas.add(m);
            return m;
        });
    }

    public void rehydrateSnapshots(TemplateRepository repo) {
        if (repo == null) return;
        for (Placement p : placements) {
            TemplateDef src = repo.findById(p.templateId);
            if (src == null) {
                p.dataSnap = null;
                continue;
            }
            // If regionIndex >=0 slice; else keep whole (animated or static)
            if (p.regionIndex >= 0) {
                p.dataSnap = TemplateSlice.copyRegion(src, Math.max(0, p.srcXpx / Math.max(1, src.tileWidthPx)), Math.max(0, p.srcYpx / Math.max(1, src.tileHeightPx)), Math.max(0, (p.srcXpx + p.srcWpx - 1) / Math.max(1, src.tileWidthPx)), Math.max(0, (p.srcYpx + p.srcHpx - 1) / Math.max(1, src.tileHeightPx)));
            } else {
                p.dataSnap = TemplateSlice.copyWhole(src);
            }
        }
    }

    public enum Base {X, Y, Z}

    // ---- gate metadata / links ----
    public record GateRef(String pid, int gateIndex) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GateRef(String pid1, int index))) return false;
            return gateIndex == index && Objects.equals(pid, pid1);
        }

        @Override
        public String toString() {
            return pid + "#gate" + gateIndex;
        }
    }

    public static final class GateMeta {
        public final GateRef ref;
        public String name = "";

        public GateMeta(GateRef ref) {
            this.ref = ref;
        }
    }

    public static final class GateLink {
        private static final AtomicInteger CT = new AtomicInteger(1);
        public final String id = "GL-" + CT.getAndIncrement();
        public GateRef a, b;
        public String name = "";

        public GateLink() {
        }

        public GateLink(GateRef a, GateRef b, String name) {
            this.a = a;
            this.b = b;
            this.name = name;
        }

        public boolean involves(GateRef r) {
            return (a != null && a.equals(r)) || (b != null && b.equals(r));
        }
    }

    // ---- placement ----
    public static final class Placement {
        public final String pid = UUID.randomUUID().toString();

        public String templateId;
        public int regionIndex; // -1 whole, -2 animated, >=0 region index

        public int gx, gy;     // top-left in tiles
        public int wTiles, hTiles; // base (unscaled) tiles

        public int srcXpx, srcYpx, srcWpx, srcHpx; // first frame / region source rect
        public int layer = 0;
        public double scale = 1.0;
        public int rotQ = 0;   // 0..3 -> 0°, 90°, 180°, 270°

        /**
         * Tilt mode:
         * 0 = none
         * 1 = forward (around U axis)
         * 2 = sideways (around V axis)
         * 3 = oblique 1 (around U+V)
         * 4 = oblique 2 (around U-V)
         */
        public int tiltMode = 0;

        /**
         * Tilt angle in degrees (0..360).
         */
        public double tiltDegrees = 0.0;

        /** If true, the per-layer extra offset on the fixed axis is inverted (goes “backwards”). */
        public boolean invertLayerOffset = false;

        public transient TemplateDef dataSnap; // runtime only

        public Placement() {
        }

        public Placement(String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles, int srcXpx, int srcYpx, int srcWpx, int srcHpx, TemplateDef snap, int layer, double scale) {
            this.templateId = templateId;
            this.regionIndex = regionIndex;
            this.gx = gx;
            this.gy = gy;
            this.wTiles = Math.max(1, wTiles);
            this.hTiles = Math.max(1, hTiles);
            this.srcXpx = srcXpx;
            this.srcYpx = srcYpx;
            this.srcWpx = srcWpx;
            this.srcHpx = srcHpx;
            this.dataSnap = snap;
            this.layer = Math.max(0, layer);
            this.scale = Math.max(0.01, scale);
            this.rotQ = 0;
            this.tiltMode = 0;
            this.tiltDegrees = 0.0;
        }
    }
}
