package com.gw.editor.map;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    @SerializedName("placements")
    public List<Placement> placements = new ArrayList<>();

    /**
     * Global graph: connections between gate nodes living on any placement.
     */
    @SerializedName("gateLinks")
    public List<GateLink> gateLinks = new ArrayList<>();

    /**
     * Placed template (whole template or a region thereof).
     */
    public static class Placement {
        /**
         * Stable instance id (UUID) for cross-references (gate graph).
         */
        public String pid = UUID.randomUUID().toString();

        public String templateId;
        public int regionIndex = -1;   // -1: whole image; >=0: region
        public int gx, gy;             // map grid coords
        public int wTiles, hTiles;     // footprint

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
}
