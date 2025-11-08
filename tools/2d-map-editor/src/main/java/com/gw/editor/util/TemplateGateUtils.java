package com.gw.editor.util;

import com.gw.editor.template.TemplateDef;

import java.util.*;

/**
 * Compute gate "islands" from a TemplateDef snapshot (tiles start at 0,0).
 */
public final class TemplateGateUtils {
    private TemplateGateUtils() {
    }

    /**
     * Each island = list of (x,y) tile coords.
     */
    public static List<List<int[]>> computeGateIslands(TemplateDef snap) {
        if (snap == null || snap.tiles == null || snap.tiles.isEmpty()) return List.of();

        // Infer width/height in tiles from image size & tile size.
        int cols = Math.max(1, snap.imageWidthPx / Math.max(1, snap.tileWidthPx));
        int rows = Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx));

        boolean[][] gate = new boolean[rows][cols];
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                TemplateDef.TileDef td = snap.tileAt(gx, gy);
                gate[gy][gx] = td != null && td.gate;
            }
        }

        List<List<int[]>> islands = new ArrayList<>();
        boolean[][] vis = new boolean[rows][cols];
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
