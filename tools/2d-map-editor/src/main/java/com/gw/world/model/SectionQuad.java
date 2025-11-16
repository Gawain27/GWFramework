package com.gw.world.model;

public class SectionQuad {
    public WorldDef.SectionPlacement section;
    public double x0, y0, z0;
    public double x1, y1, z1;
    public double x2, y2, z2;
    public double x3, y3, z3;
    public double avgZ;

    public SectionQuad(WorldDef.SectionPlacement section,
                double x0, double y0, double z0,
                double x1, double y1, double z1,
                double x2, double y2, double z2,
                double x3, double y3, double z3) {
        this.section = section;
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
        this.x3 = x3; this.y3 = y3; this.z3 = z3;
        this.avgZ = (z0 + z1 + z2 + z3) * 0.25;
    }
}
