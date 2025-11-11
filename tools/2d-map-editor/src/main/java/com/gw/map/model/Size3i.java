package com.gw.map.model;

/** Immutable-like container for 3D integer dimensions in tiles. */
public class Size3i {
    public int widthX;
    public int heightY;
    public int depthZ;
    public Size3i() {}
    public Size3i(int widthX, int heightY, int depthZ) {
        this.widthX = widthX; this.heightY = heightY; this.depthZ = depthZ;
    }
}
