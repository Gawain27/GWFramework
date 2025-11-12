package com.gw.map.model;

import com.gw.editor.template.TemplateDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal per-plane 2D editing model:
 * - tile grid (widthTiles x heightTiles)
 * - placements (instances of TemplateDef snapshots)
 * - rendering layers
 * - gate metadata + links (by placement + island index)
 */
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

    public static Plane2DMap from3D(MapDef map3d, SelectionState.BasePlane base, int index) {
        Plane2DMap pm = new Plane2DMap();
        pm.base = switch (base) {
            case X -> Base.X;
            case Y -> Base.Y;
            case Z -> Base.Z;
        };
        pm.planeIndex = index;
        // size across the two free axes:
        switch (base) {
            case Z -> {
                pm.widthTiles = map3d.size.widthX;
                pm.heightTiles = map3d.size.heightY;
            }
            case X -> {
                pm.widthTiles = map3d.size.heightY;
                pm.heightTiles = map3d.size.depthZ;
            }
            case Y -> {
                pm.widthTiles = map3d.size.widthX;
                pm.heightTiles = map3d.size.depthZ;
            }
        }
        return pm;
    }

    public void normalizeLayers() {
        if (layers.isEmpty()) layers.add(0);
        // Ensure layer indices are dense [0..n-1]
        for (Placement p : placements) p.layer = Math.max(0, Math.min(p.layer, layers.size() - 1));
    }

    public Optional<GateMeta> findGateMeta(GateRef ref) {
        return gateMetas.stream().filter(m -> m.ref.equals(ref)).findFirst();
    }

    public GateMeta ensureGateMeta(GateRef ref) {
        return findGateMeta(ref).orElseGet(() -> {
            GateMeta m = new GateMeta(ref);
            gateMetas.add(m);
            return m;
        });
    }

    public enum Base {X, Y, Z}

    // ------- gate metadata / links -------
        public record GateRef(String pid, int gateIndex) {

        @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof GateRef r)) return false;
                return gateIndex == r.gateIndex && Objects.equals(pid, r.pid);
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

        public GateLink(GateRef a, GateRef b, String name) {
            this.a = a;
            this.b = b;
            this.name = name;
        }

        public boolean involves(GateRef r) {
            return (a != null && a.equals(r)) || (b != null && b.equals(r));
        }
    }

    // ------- placement -------
    public static final class Placement {
        public final String pid = UUID.randomUUID().toString();

        public String templateId;
        public int regionIndex; // -1 full, -2 animated, >=0 region index

        public int gx, gy;     // top-left in tiles
        public int wTiles, hTiles; // base (unscaled) tiles

        public int srcXpx, srcYpx, srcWpx, srcHpx; // source rect (first frame if animated)

        public int layer = 0;
        public double scale = 1.0;

        public transient TemplateDef dataSnap; // snapshot for overlays

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
        }
    }
}
