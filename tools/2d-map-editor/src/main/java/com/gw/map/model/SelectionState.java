package com.gw.map.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


/**
 * Tracks selected base plane (one of X=0, Y=0, Z=0) and per-tile selections when in TILE_SELECTION mode.
 */

/**
 * Tracks selected base plane, its index (k), and tiles when in
 * TILE_SELECTION.
 */
public class SelectionState {

    public enum BasePlane {X, Y, Z} // base axes

    public BasePlane base = BasePlane.Z; // which family (X=k, Y=k, Z=k)
    public int index = 0; // k value in [0..sizeAlong(base)-1]
    public final Set<TileKey> selectedTiles = new HashSet<>();

    public void clearTiles() {
        selectedTiles.clear();
    }

    // 2D tile within the selected plane (axes depend on base)
    public static final class TileKey {
        public final int a;
        public final int b;

        public TileKey(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TileKey t)) return false;
            return a == t.a && b == t.b;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }
}
