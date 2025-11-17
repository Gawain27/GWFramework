package com.gw.world.model;

import com.gw.map.model.MapDef;
import com.gw.map.model.Plane2DMap;
import com.gw.map.model.Size3i;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * World = container of MapDef "sections" placed in a larger 3D tile space.
 */
public class WorldDef {

    public String id = "";
    public String name = "";

    /** World dimensions in tiles (same X/Y/Z semantics as MapDef.size). */
    public Size3i size = new Size3i(64, 32, 64);

    // Camera for the world view (mirrors MapDef camera).
    public double cameraYawDeg = 35;
    public double cameraPitchDeg = 45;
    public double cameraRollDeg = 0;
    public double cameraPanX = 0;
    public double cameraPanY = 0;
    public double cameraZoom = 1.0;

    /** All sections (MapDef instances) placed in world space. */
    public List<SectionPlacement> sections = new ArrayList<>();
    /** World-level gate links (across sections and planes). */
    public final List<GateLink> gateLinks = new ArrayList<>();


    public static WorldDef createDefault() {
        return new WorldDef();
    }

    /**
     * Rehydrate transient MapDef references for all sections.
     * Caller supplies a resolver from mapId → MapDef (or null if not found).
     */
    public void rehydrateSections(Function<String, MapDef> resolver) {
        if (resolver == null || sections == null) return;
        for (SectionPlacement sp : sections) {
            if (sp == null || sp.mapId == null || sp.mapId.isBlank()) {
                if (sp != null) sp.map = null;
                continue;
            }
            sp.map = resolver.apply(sp.mapId);
        }
    }

    /**
     * Simple helper to add a new section at a given origin.
     * MapDef is stored transiently; only mapId is persisted.
     */
    public SectionPlacement addSection(MapDef map, int wx, int wy, int wz) {
        if (map == null || map.id == null || map.id.isBlank()) {
            throw new IllegalArgumentException("MapDef with a non-empty id is required");
        }
        SectionPlacement sp = new SectionPlacement(map.id, wx, wy, wz);
        sp.map = map;
        sections.add(sp);
        return sp;
    }

    /**
     * Clamp all sections so they stay within the world bounds.
     * (Sections with unknown map sizes are left untouched.)
     */
    public void clampSectionsToWorld() {
        if (sections == null) return;
        for (SectionPlacement sp : sections) {
            if (sp == null || sp.map == null || sp.map.size == null) continue;
            clampSectionToWorld(sp);
        }
    }

    /**
     * Ensures one section's origin stays inside the world bounds,
     * considering the section's own MapDef.size.
     */
    public void clampSectionToWorld(SectionPlacement sp) {
        if (sp == null || sp.map == null || sp.map.size == null) return;
        int maxX = Math.max(0, size.widthX  - sp.map.size.widthX);
        int maxY = Math.max(0, size.heightY - sp.map.size.heightY);
        int maxZ = Math.max(0, size.depthZ  - sp.map.size.depthZ);

        sp.wx = clamp(sp.wx, 0, maxX);
        sp.wy = clamp(sp.wy, 0, maxY);
        sp.wz = clamp(sp.wz, 0, maxZ);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /**
     * A placed MapDef in world space. Origin (wx,wy,wz) is the MapDef's (0,0,0).
     */
    public static final class SectionPlacement {
        public final String id = UUID.randomUUID().toString();

        /** ID of the MapDef (matches MapDef.id, persisted in JSON). */
        public String mapId;

        /** Runtime-only hydrated reference, not persisted. */
        public transient MapDef map;

        /** Origin of this section in world tile coordinates. */
        public int wx, wy, wz;

        public double rotXDeg = 0.0; // rotate around X
        public double rotYDeg = 0.0; // rotate around Y
        public double rotZDeg = 0.0; // rotate around Z

        public SectionPlacement() {
        }

        public SectionPlacement(String mapId, int wx, int wy, int wz) {
            this.mapId = mapId;
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
        }

        public void moveBy(int dx, int dy, int dz) {
            this.wx += dx;
            this.wy += dy;
            this.wz += dz;
        }

        @Override
        public String toString() {
            return "Section[" + mapId + "] @" + "(" + wx + "," + wy + "," + wz + ")";
        }
    }

    /** Endpoint of a gate in the world (section + plane + placement + gate index). */
    public static final class GateEndpoint {
        /** Index into world.sections list. */
        public int sectionIndex;

        /** Plane key inside the section's map ("Z=0", "X=1", "Y=2", etc.). */
        public String planeKey;

        /** Reference to a gate island inside that plane/map. */
        public Plane2DMap.GateRef gateRef;

        public GateEndpoint() {
        }

        public GateEndpoint(int sectionIndex, String planeKey, Plane2DMap.GateRef gateRef) {
            this.sectionIndex = sectionIndex;
            this.planeKey = planeKey;
            this.gateRef = gateRef;
        }
    }

    /** World-level link between two gate endpoints. */
    public static final class GateLink {
        private static final AtomicInteger CT = new AtomicInteger(1);
        public final String id = "WGL-" + CT.getAndIncrement();

        public GateEndpoint a;
        public GateEndpoint b;
        public String name = "";

        public GateLink() {}

        public GateLink(GateEndpoint a, GateEndpoint b, String name) {
            this.a = a;
            this.b = b;
            this.name = name;
        }

        public boolean involves(GateEndpoint ep) {
            if (ep == null) return false;
            return equalsEndpoint(a, ep) || equalsEndpoint(b, ep);
        }

        private static boolean equalsEndpoint(GateEndpoint x, GateEndpoint y) {
            if (x == y) return true;
            if (x == null || y == null) return false;
            if (x.sectionIndex != y.sectionIndex) return false;
            if (x.planeKey == null ? y.planeKey != null : !x.planeKey.equals(y.planeKey)) return false;
            if (x.gateRef == null || y.gateRef == null) return false;
            return x.gateRef.equals(y.gateRef);
        }
    }
}
