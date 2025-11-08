package com.gw.editor.map;

import com.google.gson.annotations.SerializedName;
import com.gw.editor.template.TemplateDef;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A tile-based map in tile units; origin is (0,0).
 */
public class MapDef {
    public String id = "";
    public int tileWidthPx = 16;
    public int tileHeightPx = 16;

    public int widthTiles = 64;
    public int heightTiles = 36;

    public String notes;

    /**
     * Optional background asset (drawn below layer 0).
     */
    @SerializedName("background")
    public Background background;

    public static class Background {
        /**
         * logical path of the asset used as background
         */
        public String logicalPath;
        /**
         * image size in pixels (cached for layout convenience)
         */
        public int imageWidthPx;
        public int imageHeightPx;
    }

    /**
     * Ordered list of layers (0..N-1). Index in this list is draw order.
     */
    @SerializedName("layers")
    public List<Integer> layers = new ArrayList<>(List.of(0));  // default: one layer (0)

    @SerializedName("placements")
    public List<Placement> placements = new ArrayList<>();

    /**
     * Gate metadata: human-readable names (per placement’s gate island).
     */
    @SerializedName("gateMetas")
    public List<GateMeta> gateMetas = new ArrayList<>();

    /**
     * Global graph: connections between gate nodes living on any placement.
     */
    @SerializedName("gateLinks")
    public List<GateLink> gateLinks = new ArrayList<>();

    /**
     * Placed template (whole texture or a region); holds a SNAPSHOT of TemplateDef data.
     */
    public static class Placement {
        public String pid = java.util.UUID.randomUUID().toString();
        public String templateId;
        public int regionIndex = -1;   // -1: whole image; >=0: region
        public int gx, gy;             // map grid coords
        public int wTiles, hTiles;     // base footprint in tiles (before scale)
        public int srcXpx, srcYpx, srcWpx, srcHpx; // source rect
        public com.gw.editor.template.TemplateDef dataSnap = new com.gw.editor.template.TemplateDef();
        public int layer = 0;

        /** Scale multiplier in tiles (applied on draw/placement footprint). */
        public double scale = 1.0;

        public Placement() {}

        public Placement(String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles,
                         int srcXpx, int srcYpx, int srcWpx, int srcHpx,
                         com.gw.editor.template.TemplateDef dataSnap, int layer, double scale) {
            this.templateId = templateId;
            this.regionIndex = regionIndex;
            this.gx = gx; this.gy = gy;
            this.wTiles = wTiles; this.hTiles = hTiles;
            this.srcXpx = srcXpx; this.srcYpx = srcYpx; this.srcWpx = srcWpx; this.srcHpx = srcHpx;
            this.dataSnap = dataSnap;
            this.layer = layer;
            this.scale = scale <= 0 ? 1.0 : scale;
        }
    }

    /**
     * A reference to a gate island inside a placement instance.
     */
    public static class GateRef {
        public String pid;      // placement pid
        public int gateIndex;   // 0..N-1 (island index)

        public GateRef() {
        }

        public GateRef(String pid, int gateIndex) {
            this.pid = pid;
            this.gateIndex = gateIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GateRef gr)) return false;
            return gateIndex == gr.gateIndex && Objects.equals(pid, gr.pid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pid, gateIndex);
        }

        @Override
        public String toString() {
            return pid + "#gate" + gateIndex;
        }
    }

    /**
     * Human label + UUID for a particular GateRef.
     */
    public static class GateMeta {
        public String id = UUID.randomUUID().toString(); // stable id
        public String name = "";                         // editable display name
        public GateRef ref = new GateRef();             // identifies the island in a placement

        public GateMeta() {
        }

        public GateMeta(GateRef ref, String name) {
            this.ref = ref;
            this.name = (name == null ? "" : name);
        }
    }

    /**
     * Undirected connection between two gate refs.
     */
    public static class GateLink {
        public String id = UUID.randomUUID().toString(); // stable id
        public String name = "";                         // editable display name (optional)
        public GateRef a;
        public GateRef b;

        public GateLink() {
        }

        public GateLink(GateRef a, GateRef b, String name) {
            this.a = a;
            this.b = b;
            this.name = (name == null ? "" : name);
        }

        public boolean involves(GateRef r) {
            return (a != null && a.equals(r)) || (b != null && b.equals(r));
        }
    }

    /* ─────────────── Helpers for layer management ─────────────── */

    public void normalizeLayers() {
        if (layers == null || layers.isEmpty()) layers = new ArrayList<>(List.of(0));
        int max = layers.size() - 1;
        for (Placement p : placements) {
            if (p.layer < 0) p.layer = 0;
            if (p.layer > max) p.layer = max;
        }
    }

    public int addLayer() {
        int idx = layers.size();
        layers.add(idx);
        return idx;
    }

    public void removeLayer(int removeIdx) {
        if (layers.size() <= 1) return;
        if (removeIdx < 0 || removeIdx >= layers.size()) return;

        Set<String> removedPids = placements.stream()
            .filter(p -> p.layer == removeIdx)
            .map(p -> p.pid)
            .collect(Collectors.toSet());

        placements.removeIf(p -> p.layer == removeIdx);
        for (Placement p : placements) if (p.layer > removeIdx) p.layer--;

        gateLinks.removeIf(gl ->
            (gl.a != null && removedPids.contains(gl.a.pid)) ||
                (gl.b != null && removedPids.contains(gl.b.pid))
        );
        gateMetas.removeIf(gm -> removedPids.contains(gm.ref.pid));

        layers.remove(removeIdx);
        for (int i = 0; i < layers.size(); i++) layers.set(i, i);

        normalizeLayers();
    }

    /* ─────────────── Gate meta helpers ─────────────── */
    public Optional<GateMeta> findGateMeta(GateRef ref) {
        return gateMetas.stream().filter(m -> m.ref.equals(ref)).findFirst();
    }

    public GateMeta ensureGateMeta(GateRef ref) {
        return findGateMeta(ref).orElseGet(() -> {
            GateMeta m = new GateMeta(ref, "");
            gateMetas.add(m);
            return m;
        });
    }
}
