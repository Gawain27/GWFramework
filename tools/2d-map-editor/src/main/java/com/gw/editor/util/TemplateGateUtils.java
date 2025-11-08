package com.gw.editor.util;

import com.gw.editor.template.TemplateDef;

import java.util.*;

/**
 * Utility to compute gate "islands" (connected green tiles) within a template/region.
 */
public final class TemplateGateUtils {
    private TemplateGateUtils() {
    }

    /**
     * Returns list of islands; each island is a list of (x,y) tile coords relative to the region origin.
     */
    public static List<List<int[]>> computeGateIslands(TemplateDef t, int regionXpx, int regionYpx,
                                                       int regionWpx, int regionHpx,
                                                       int tileWpx, int tileHpx) {

        if (t == null || t.tiles == null || t.tiles.isEmpty()) return List.of();

        int cols = Math.max(1, regionWpx / Math.max(1, tileWpx));
        int rows = Math.max(1, regionHpx / Math.max(1, tileHpx));

        boolean[][] gate = new boolean[rows][cols];
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                int imgX = regionXpx + gx * tileWpx;
                int imgY = regionYpx + gy * tileHpx;
                // tile index in template tile grid
                int ti = (imgY / Math.max(1, t.tileHeightPx)) * Math.max(1, (t.imageWidthPx / Math.max(1, t.tileWidthPx))) + (imgX / Math.max(1, t.tileWidthPx));
                if (ti >= 0 && ti < t.tiles.size()) {
                    TemplateDef.TileDef props = t.tiles.get(gx + "," + gy);
                    gate[gy][gx] = props != null && props.gate; // "green" = gate
                }
            }
        }

        // flood-fill 4-neighbour
        boolean[][] vis = new boolean[rows][cols];
        List<List<int[]>> islands = new ArrayList<>();
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};

        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++) {
                if (!gate[y][x] || vis[y][x]) continue;
                List<int[]> comp = new ArrayList<>();
                Deque<int[]> dq = new ArrayDeque<>();
                dq.add(new int[]{x, y});
                vis[y][x] = true;
                while (!dq.isEmpty()) {
                    int[] p = dq.removeFirst();
                    comp.add(p);
                    for (int k = 0; k < 4; k++) {
                        int nx = p[0] + dx[k], ny = p[1] + dy[k];
                        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                        if (!gate[ny][nx] || vis[ny][nx]) continue;
                        vis[ny][nx] = true;
                        dq.addLast(new int[]{nx, ny});
                    }
                }
                islands.add(comp);
            }
        return islands;
    }
}
