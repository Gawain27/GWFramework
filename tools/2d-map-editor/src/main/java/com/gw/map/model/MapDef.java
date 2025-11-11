package com.gw.map.model;

import com.google.gson.annotations.SerializedName;


/**
 * Authoring data for a 3D grid map. Fixed size in tiles.
 * Background may be defined by a template placed on the Z=0 (bottom) plane.
 */
public class MapDef {
    @SerializedName("schema")
    public int schema = 1;
    public String id;
    public String name;
    public Size3i size; // widthX, heightY, depthZ
    // Camera state
    public double cameraPanX = 0;
    public double cameraPanY = 0;
    public double cameraZoom = 1.0;
    public double cameraPitchDeg = 35.0; // initial in MVP
    public double cameraYawDeg = 45.0; // right-drag adjusts yaw
    public double cameraRollDeg = 0.0;   // optional; wired in renderer, not exposed in UI yet


    public static MapDef createDefault() {
        MapDef m = new MapDef();
        m.id = "untitled";
        m.name = "Untitled";
        m.size = new Size3i(16, 16, 6);
        return m;
    }
}
