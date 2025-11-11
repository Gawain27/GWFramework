package com.gw.map.model;

public class Quad {
    public Kind kind;
    public int index; // plane index for this kind
    public double x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;
    public double avgZ;
    public Quad(Kind kind, int index, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3) {
        this.kind = kind;
        this.index = index;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.x3 = x3;
        this.y3 = y3;
        this.z3 = z3;
        this.avgZ = (z0 + z1 + z2 + z3) * 0.25; // pre-rotation heuristic; we sort later anyway by transformed Z via project
    }

}
