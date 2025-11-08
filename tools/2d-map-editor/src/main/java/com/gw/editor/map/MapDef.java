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
     * Ordered list of layers (0..N-1). Index in this list is draw order.
     */
    @SerializedName("layers")
    public List<Integer> layers = new ArrayList<>(List.of(0));  // default: one layer (0)

    @SerializedName("placements")
    public List<Placement> placements = new ArrayList<>();

    /**
     * Global graph: connections between gate nodes living on any placement.
     */
    @SerializedName("gateLinks")
    public List<GateLink> gateLinks = new ArrayList<>();

    /**
     * Placed template (whole texture or a region); holds a SNAPSHOT of TemplateDef data.
     */
    public static class Placement {
        /**
         * Stable instance id (UUID) for cross-references (gate graph).
         */
        public String pid = UUID.randomUUID().toString();

        /**
         * Authoring source identity (for provenance/debug only).
         */
        public String templateId;
        /**
         * -1: whole image; >=0: region index from the source template (if any).
         */
        public int regionIndex = -1;

        /**
         * Assigned drawing layer (0..layers.size()-1).
         */
        public int layer = 0;

        /**
         * Top-left grid position on the map.
         */
        public int gx, gy;

        /**
         * Footprint in tiles (matches dataSnap’s extents).
         */
        public int wTiles, hTiles;

        /**
         * Sprite source rect (pixels) inside the original texture; used for drawing.
         */
        public int srcXpx, srcYpx, srcWpx, srcHpx;

        /**
         * Per-instance immutable snapshot of the template data used here (cropped if region).
         */
        public TemplateDef dataSnap = new TemplateDef();

        public Placement() {
        }

        public Placement(String templateId, int regionIndex, int gx, int gy,
                         int wTiles, int hTiles,
                         int srcXpx, int srcYpx, int srcWpx, int srcHpx,
                         TemplateDef dataSnap, int layer) {
            this.templateId = templateId;
            this.regionIndex = regionIndex;
            this.gx = gx;
            this.gy = gy;
            this.wTiles = wTiles;
            this.hTiles = hTiles;
            this.srcXpx = srcXpx;
            this.srcYpx = srcYpx;
            this.srcWpx = srcWpx;
            this.srcHpx = srcHpx;
            this.dataSnap = dataSnap;
            this.layer = layer;
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
     * Undirected connection between two gate refs.
     */
    public static class GateLink {
        public GateRef a;
        public GateRef b;

        public GateLink() {
        }

        public GateLink(GateRef a, GateRef b) {
            this.a = a;
            this.b = b;
        }

        public boolean involves(GateRef r) {
            return (a != null && a.equals(r)) || (b != null && b.equals(r));
        }
    }

    /* ─────────────── Helpers for layer management ─────────────── */

    /**
     * Ensure at least one layer exists and all placement layers are in range.
     */
    public void normalizeLayers() {
        if (layers == null || layers.isEmpty()) layers = new ArrayList<>(List.of(0));
        int max = layers.size() - 1;
        for (Placement p : placements) {
            if (p.layer < 0) p.layer = 0;
            if (p.layer > max) p.layer = max;
        }
    }

    /**
     * Append a new layer at the bottom; returns its index.
     */
    public int addLayer() {
        int idx = layers.size();
        layers.add(idx);
        return idx;
    }

    /**
     * Remove the layer at given index – deletes all placements on that layer,
     * removes gate links referencing those placements, and shifts higher layers down.
     */
    public void removeLayer(int removeIdx) {
        if (layers.size() <= 1) return;  // keep at least one layer
        if (removeIdx < 0 || removeIdx >= layers.size()) return;

        // Collect PIDs to be removed
        Set<String> removedPids = placements.stream()
            .filter(p -> p.layer == removeIdx)
            .map(p -> p.pid)
            .collect(Collectors.toSet());

        // Remove placements on that layer
        placements.removeIf(p -> p.layer == removeIdx);

        // Shift layers of remaining placements above removed layer
        for (Placement p : placements) if (p.layer > removeIdx) p.layer--;

        // Purge gate links involving removed placements
        gateLinks.removeIf(gl ->
            (gl.a != null && removedPids.contains(gl.a.pid)) ||
                (gl.b != null && removedPids.contains(gl.b.pid))
        );

        // Remove layer entry and reindex remaining layer ids to 0..N-1
        layers.remove(removeIdx);
        for (int i = 0; i < layers.size(); i++) layers.set(i, i);

        normalizeLayers();
    }
}
