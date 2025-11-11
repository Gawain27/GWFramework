package com.gw.map.model;

/**
 * Result for plane picking.
 */
public class PlaneHit {
    public SelectionState.BasePlane base;
    public int index;
    public PlaneHit(Kind base, int index) {
        this.base = switch (base) {
            case Z -> SelectionState.BasePlane.Z;
            case X -> SelectionState.BasePlane.X;
            case Y -> SelectionState.BasePlane.Y;
        };
        this.index = index;
    }
}
