package com.gw.map.model;

import java.util.*;

public class MapDef {
    public String id = "";
    public String name = "";
    public Size3i size = new Size3i(16, 8, 16);

    public double cameraYawDeg = 35;
    public double cameraPitchDeg = 45;
    public double cameraRollDeg = 0;
    public double cameraPanX = 0;
    public double cameraPanY = 0;
    public double cameraZoom = 1.0;

    /** Keyed by "X=k", "Y=k", or "Z=k". */
    public Map<String, Plane2DMap> planes = new HashMap<>();

    public static MapDef createDefault() {
        return new MapDef();
    }

    public String planeKey(SelectionState.BasePlane base, int index) {
        return switch (base) {
            case X -> "X=" + index;
            case Y -> "Y=" + index;
            case Z -> "Z=" + index;
        };
    }

    /** Get or create a plane (shared by 3D and plane editor). */
    public Plane2DMap getOrCreatePlane(SelectionState.BasePlane base, int index) {
        String key = planeKey(base, index);
        return planes.computeIfAbsent(key, k -> Plane2DMap.fromSize(base, index,
            switch (base) {
                case Z -> size.widthX;
                case X -> size.heightY;
                case Y -> size.widthX;
            },
            switch (base) {
                case Z -> size.heightY;
                case X -> size.depthZ;
                case Y -> size.depthZ;
            }
        ));
    }
}
