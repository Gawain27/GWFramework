package com.gw.map.model;

public class TileQuad extends Quad {
    public int a, b; // plane-local coordinates (returned for selection)

    public TileQuad(Kind kind, int index, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int a, int b) {
        super(kind, index, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        this.a = a;
        this.b = b;
    }
}
