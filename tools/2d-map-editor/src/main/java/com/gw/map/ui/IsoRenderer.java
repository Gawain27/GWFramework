package com.gw.map.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateSlice;
import com.gw.map.io.DefaultTextureResolver;
import com.gw.map.io.TextureResolver;
import com.gw.map.model.Kind;
import com.gw.map.model.MapDef;
import com.gw.map.model.Plane2DMap;
import com.gw.map.model.PlaneHit;
import com.gw.map.model.Quad;
import com.gw.map.model.SelectionState;
import com.gw.map.model.TileQuad;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 3D-ish orthographic renderer with camera yaw/pitch/roll.
 * - In tile-select mode: only the selected plane + its placements render.
 * - Placements are drawn tile-by-tile on the plane, so they remain perfectly glued to the plane grid.
 */
public class IsoRenderer {

    private final Canvas canvas;
    private final double baseScale = 32.0; // pixels per world unit before zoom

    private MapDef map;
    private TextureResolver textureResolver = new DefaultTextureResolver();
    private TemplateRepository templateRepo = new TemplateRepository();

    public IsoRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    // R = Rz(roll) * Rx(pitch) * Ry(yaw)
    private static double[][] rotationMatrix(double yawDeg, double pitchDeg, double rollDeg) {
        double cy = Math.cos(Math.toRadians(yawDeg)), sy = Math.sin(Math.toRadians(yawDeg));
        double cx = Math.cos(Math.toRadians(pitchDeg)), sx = Math.sin(Math.toRadians(pitchDeg));
        double cz = Math.cos(Math.toRadians(rollDeg)), sz = Math.sin(Math.toRadians(rollDeg));
        double[][] Ry = {{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        double[][] Rx = {{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        double[][] Rz = {{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};
        return mul(Rz, mul(Rx, Ry));
    }

    private static double[][] mul(double[][] A, double[][] B) {
        double[][] R = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                R[i][j] = A[i][0] * B[0][j] + A[i][1] * B[1][j] + A[i][2] * B[2][j];
        return R;
    }

    // Orthographic projection after rotation; screen y grows down → invert Y
    private static double[] project(double[][] R, double scale, double ox, double oy, double x, double y, double z) {
        double xr = R[0][0] * x + R[0][1] * y + R[0][2] * z;
        double yr = R[1][0] * x + R[1][1] * y + R[1][2] * z;
        double zr = R[2][0] * x + R[2][1] * y + R[2][2] * z;
        return new double[]{ox + xr * scale, oy - yr * scale, zr};
    }

    /* ---------------- Camera math ---------------- */

    private static boolean pointInTri(double px, double py, double[] a, double[] b, double[] c) {
        double v0x = c[0] - a[0], v0y = c[1] - a[1];
        double v1x = b[0] - a[0], v1y = b[1] - a[1];
        double v2x = px - a[0], v2y = py - a[1];
        double dot00 = v0x * v0x + v0y * v0y;
        double dot01 = v0x * v1x + v0y * v1y;
        double dot02 = v0x * v2x + v0y * v2y;
        double dot11 = v1x * v1x + v1y * v1y;
        double dot12 = v1x * v2x + v1y * v2y;
        double invDen = 1.0 / (dot00 * dot11 - dot01 * dot01 + 1e-9);
        double u = (dot11 * dot02 - dot01 * dot12) * invDen;
        double v = (dot00 * dot12 - dot01 * dot02) * invDen;
        return (u >= 0) && (v >= 0) && (u + v <= 1);
    }

    private static boolean pointInQuad(double sx, double sy, double[] a, double[] b, double[] c, double[] d) {
        return pointInTri(sx, sy, a, b, c) || pointInTri(sx, sy, a, c, d);
    }

    public void setMap(MapDef map) {
        this.map = map;
    }

    public void setTextureResolver(TextureResolver r) {
        if (r != null) this.textureResolver = r;
    }

    public void setTemplateRepository(TemplateRepository repo) {
        if (repo != null) this.templateRepo = repo;
    }

    /* ---------------- Public render ---------------- */

    public void render(GraphicsContext g, MapDef map, SelectionState sel, boolean tileMode, String ghostTemplateId, double[] ghostScreenPos) {

        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (map == null) return;

        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        // Axes with labels
        drawAxes(g, R, scale, ox, oy, map);

        // Placements (in tile mode, only the selected plane)
        if (tileMode) drawPlanePlacementsFiltered(g, map, R, scale, ox, oy, sel.base, sel.index);
        else drawPlanePlacementsAll(g, map, R, scale, ox, oy);

        // Build plane/tile quads for grid
        List<Quad> quads = new ArrayList<>();
        if (tileMode) {
            switch (sel.base) {
                case Z -> addPlaneZ(quads, sel.index);
                case X -> addPlaneX(quads, sel.index);
                case Y -> addPlaneY(quads, sel.index);
            }
        } else {
            for (int k = 0; k < map.size.depthZ; k++) addPlaneZ(quads, k);
            for (int k = 0; k < map.size.widthX; k++) addPlaneX(quads, k);
            for (int k = 0; k < map.size.heightY; k++) addPlaneY(quads, k);
        }

        quads.sort(Comparator.comparingDouble(q -> -q.avgZ)); // back-to-front

        for (Quad q : quads) {
            boolean highlightPlane = (!tileMode) && ((q.kind == Kind.Z && q.index == (sel.base == SelectionState.BasePlane.Z ? sel.index : -1)) || (q.kind == Kind.X && q.index == (sel.base == SelectionState.BasePlane.X ? sel.index : -1)) || (q.kind == Kind.Y && q.index == (sel.base == SelectionState.BasePlane.Y ? sel.index : -1)));

            Color line = highlightPlane ? Color.rgb(0, 128, 255, 0.9) : Color.rgb(0, 0, 0, 0.12);
            Color fill = switch (q.kind) {
                case Z -> Color.rgb(0, 0, 0, tileMode ? 0.05 : 0.03);
                case X -> Color.rgb(0, 0, 0, tileMode ? 0.06 : 0.02);
                case Y -> Color.rgb(0, 0, 0, tileMode ? 0.06 : 0.02);
            };

            double[] s0 = project(R, scale, ox, oy, q.x0, q.y0, q.z0);
            double[] s1 = project(R, scale, ox, oy, q.x1, q.y1, q.z1);
            double[] s2 = project(R, scale, ox, oy, q.x2, q.y2, q.z2);
            double[] s3 = project(R, scale, ox, oy, q.x3, q.y3, q.z3);

            g.setFill(fill);
            g.fillPolygon(new double[]{s0[0], s1[0], s2[0], s3[0]}, new double[]{s0[1], s1[1], s2[1], s3[1]}, 4);
            g.setStroke(line);
            g.setLineWidth(highlightPlane ? 2.0 : 1.0);
            g.strokePolygon(new double[]{s0[0], s1[0], s2[0], s3[0]}, new double[]{s0[1], s1[1], s2[1], s3[1]}, 4);
        }

        // Single-tile highlight in tile mode
        if (tileMode && !sel.selectedTiles.isEmpty()) {
            var tk = sel.selectedTiles.iterator().next();
            switch (sel.base) {
                case Z -> drawTileHighlight(g, R, scale, ox, oy, tk.a, tk.b, sel.index);
                case X -> drawTileHighlight(g, R, scale, ox, oy, sel.index, tk.a, tk.b);
                case Y -> drawTileHighlight(g, R, scale, ox, oy, tk.a, sel.index, tk.b);
            }
        }
    }

    /* ---------------- Axes ---------------- */

    private void drawAxes(GraphicsContext g, double[][] R, double scale, double ox, double oy, MapDef map) {
        double[] O = project(R, scale, ox, oy, 0, 0, 0);
        double[] Xp = project(R, scale, ox, oy, Math.max(1, map.size.widthX), 0, 0);
        double[] Yp = project(R, scale, ox, oy, 0, Math.max(1, map.size.heightY), 0);
        double[] Zp = project(R, scale, ox, oy, 0, 0, Math.max(1, map.size.depthZ));

        g.setLineWidth(2.0);
        g.setStroke(Color.rgb(220, 40, 40, 0.9));
        g.strokeLine(O[0], O[1], Xp[0], Xp[1]);
        g.setFill(Color.rgb(220, 40, 40, 0.95));
        g.fillText("X", Xp[0] + 6, Xp[1] - 6);
        g.setStroke(Color.rgb(40, 180, 60, 0.9));
        g.strokeLine(O[0], O[1], Yp[0], Yp[1]);
        g.setFill(Color.rgb(40, 180, 60, 0.95));
        g.fillText("Y", Yp[0] + 6, Yp[1] - 6);
        g.setStroke(Color.rgb(40, 100, 220, 0.9));
        g.strokeLine(O[0], O[1], Zp[0], Zp[1]);
        g.setFill(Color.rgb(40, 100, 220, 0.95));
        g.fillText("Z", Zp[0] + 6, Zp[1] - 6);
    }

    /* ---------------- Plane placements ---------------- */

    private void drawPlanePlacementsAll(GraphicsContext g, MapDef map, double[][] R, double scale, double ox, double oy) {
        if (map.planes == null || map.planes.isEmpty()) return;
        g.save();
        for (Plane2DMap plane : map.planes.values()) {
            if (plane == null || plane.placements.isEmpty()) continue;
            drawPlanePlacementsFor(g, plane, R, scale, ox, oy);
        }
        g.restore();
    }

    private void drawPlanePlacementsFiltered(GraphicsContext g, MapDef map, double[][] R, double scale, double ox, double oy, SelectionState.BasePlane base, int index) {
        if (map.planes == null) return;
        Plane2DMap plane = map.planes.get(map.planeKey(base, index));
        if (plane == null || plane.placements.isEmpty()) return;
        g.save();
        drawPlanePlacementsFor(g, plane, R, scale, ox, oy);
        g.restore();
    }

    /**
     * Draw placements on one plane tile-by-tile. Each source sub-rect is mapped to one world tile
     * patch, so the texture is locked to the plane grid and follows it under camera rotation.
     */
    private void drawPlanePlacementsFor(GraphicsContext g, Plane2DMap plane, double[][] R, double scale, double ox, double oy) {
        for (Plane2DMap.Placement p : plane.placements) {
            TemplateDef snap = p.dataSnap;
            if (snap == null) {
                var src = templateRepo.findById(p.templateId);
                if (src == null) continue;
                snap = (p.regionIndex >= 0) ? TemplateSlice.copyRegion(src, Math.max(0, p.srcXpx / Math.max(1, src.tileWidthPx)), Math.max(0, p.srcYpx / Math.max(1, src.tileHeightPx)), Math.max(0, (p.srcXpx + p.srcWpx - 1) / Math.max(1, src.tileWidthPx)), Math.max(0, (p.srcYpx + p.srcHpx - 1) / Math.max(1, src.tileHeightPx))) : TemplateSlice.copyWhole(src);
                p.dataSnap = snap;
            }

            String logical = (snap.logicalPath == null || snap.logicalPath.isBlank()) ? (templateRepo.findById(p.templateId) != null ? templateRepo.findById(p.templateId).logicalPath : null) : snap.logicalPath;
            if (logical == null || logical.isBlank()) continue;

            String url;
            try {
                url = textureResolver.resolve(logical);
            } catch (Exception ex) {
                continue;
            }
            if (url == null) continue;
            Image img = new Image(url, false);

            // Source rect (first frame if animated)
            int sx = p.srcXpx, sy = p.srcYpx, sw = p.srcWpx, sh = p.srcHpx;
            if (snap.complex && snap.animated && snap.regions != null && !snap.regions.isEmpty()) {
                int[] r = snap.pixelRegions().get(0);
                sx = r[0];
                sy = r[1];
                sw = r[2];
                sh = r[3];
            }

            // Placement footprint in tiles (scaled)
            int Wt = Math.max(1, (int) Math.ceil(p.wTiles * Math.max(0.01, p.scale)));
            int Ht = Math.max(1, (int) Math.ceil(p.hTiles * Math.max(0.01, p.scale)));

            // Per-tile source slice size
            double cellSw = sw / (double) Wt;
            double cellSh = sh / (double) Ht;

            // World origin (top-left tile) + plane U/V unit tile vectors
            double x0 = 0, y0 = 0, z0 = 0, uxX = 0, uxY = 0, uxZ = 0, vyX = 0, vyY = 0, vyZ = 0;
            switch (plane.base) {
                case Z -> { // z=k, U→+X, V→+Y
                    x0 = p.gx;
                    y0 = p.gy;
                    z0 = plane.planeIndex;
                    uxX = 1;
                    uxY = 0;
                    uxZ = 0;
                    vyX = 0;
                    vyY = 1;
                    vyZ = 0;
                }
                case X -> { // x=k, U→+Y, V→+Z
                    x0 = plane.planeIndex;
                    y0 = p.gx;
                    z0 = p.gy;
                    uxX = 0;
                    uxY = 1;
                    uxZ = 0;
                    vyX = 0;
                    vyY = 0;
                    vyZ = 1;
                }
                case Y -> { // y=k, U→+X, V→+Z
                    x0 = p.gx;
                    y0 = plane.planeIndex;
                    z0 = p.gy;
                    uxX = 1;
                    uxY = 0;
                    uxZ = 0;
                    vyX = 0;
                    vyY = 0;
                    vyZ = 1;
                }
            }

            // Draw each tile cell
            for (int v = 0; v < Ht; v++) {
                for (int u = 0; u < Wt; u++) {
                    // Destination: one world tile patch at (u,v) from the placement origin
                    double wx = x0 + u * uxX + v * vyX;
                    double wy = y0 + u * uxY + v * vyY;
                    double wz = z0 + u * uxZ + v * vyZ;

                    // Project the three points that define the affine for this single tile:
                    // (0,0) -> S00, (1,0) -> S10, (0,1) -> S01 (all in "tiles")
                    double[] S00 = project(R, scale, ox, oy, wx, wy, wz);
                    double[] S10 = project(R, scale, ox, oy, wx + uxX, wy + uxY, wz + uxZ);
                    double[] S01 = project(R, scale, ox, oy, wx + vyX, wy + vyY, wz + vyZ);

                    // Build affine from source sub-rect pixels to that one-tile parallelogram
                    double subSx = sx + u * cellSw;
                    double subSy = sy + v * cellSh;

                    double mxx = (S10[0] - S00[0]) / Math.max(1.0, cellSw);
                    double mxy = (S10[1] - S00[1]) / Math.max(1.0, cellSw);
                    double myx = (S01[0] - S00[0]) / Math.max(1.0, cellSh);
                    double myy = (S01[1] - S00[1]) / Math.max(1.0, cellSh);
                    double tx = S00[0];
                    double ty = S00[1];

                    g.save();
                    javafx.scene.transform.Affine A = new javafx.scene.transform.Affine(mxx, mxy, tx, myx, myy, ty);
                    g.setTransform(A);
                    g.drawImage(img, subSx, subSy, cellSw, cellSh, 0, 0, cellSw, cellSh);
                    g.restore();
                }
            }
        }
    }

    /* ---------------- Picking ---------------- */

    public PlaneHit hitTestPlane(double sx, double sy) {
        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        List<Quad> planes = new ArrayList<>();
        for (int k = 0; k < map.size.depthZ; k++) planes.add(planeZOutline(k));
        for (int k = 0; k < map.size.widthX; k++) planes.add(planeXOutline(k));
        for (int k = 0; k < map.size.heightY; k++) planes.add(planeYOutline(k));

        planes.sort(Comparator.comparingDouble(q -> -q.avgZ));
        for (Quad q : planes) {
            double[] s0 = project(R, scale, ox, oy, q.x0, q.y0, q.z0);
            double[] s1 = project(R, scale, ox, oy, q.x1, q.y1, q.z1);
            double[] s2 = project(R, scale, ox, oy, q.x2, q.y2, q.z2);
            double[] s3 = project(R, scale, ox, oy, q.x3, q.y3, q.z3);
            if (pointInQuad(sx, sy, s0, s1, s2, s3)) return new PlaneHit(q.kind, q.index);
        }
        return null;
    }

    public int[] screenToPlaneTile(SelectionState.BasePlane base, int index, double sx, double sy) {
        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        List<TileQuad> tiles = new ArrayList<>();
        switch (base) {
            case Z -> {
                for (int y = 0; y < map.size.heightY; y++)
                    for (int x = 0; x < map.size.widthX; x++) tiles.add(tileZ(x, y, index));
            }
            case X -> {
                for (int z = 0; z < map.size.depthZ; z++)
                    for (int y = 0; y < map.size.heightY; y++) tiles.add(tileX(index, y, z));
            }
            case Y -> {
                for (int z = 0; z < map.size.depthZ; z++)
                    for (int x = 0; x < map.size.widthX; x++) tiles.add(tileY(x, index, z));
            }
        }
        tiles.sort(Comparator.comparingDouble(t -> -t.avgZ));
        for (TileQuad tq : tiles) {
            double[] s0 = project(R, scale, ox, oy, tq.x0, tq.y0, tq.z0);
            double[] s1 = project(R, scale, ox, oy, tq.x1, tq.y1, tq.z1);
            double[] s2 = project(R, scale, ox, oy, tq.x2, tq.y2, tq.z2);
            double[] s3 = project(R, scale, ox, oy, tq.x3, tq.y3, tq.z3);
            if (pointInQuad(sx, sy, s0, s1, s2, s3)) return new int[]{tq.a, tq.b};
        }
        return null;
    }

    /* ---------------- Plane/tile quad builders ---------------- */

    private void addPlaneZ(List<Quad> out, int k) {
        for (int y = 0; y < map.size.heightY; y++)
            for (int x = 0; x < map.size.widthX; x++)
                out.add(tileZ(x, y, k));
    }

    private void addPlaneX(List<Quad> out, int k) {
        for (int z = 0; z < map.size.depthZ; z++)
            for (int y = 0; y < map.size.heightY; y++)
                out.add(tileX(k, y, z));
    }

    private void addPlaneY(List<Quad> out, int k) {
        for (int z = 0; z < map.size.depthZ; z++)
            for (int x = 0; x < map.size.widthX; x++)
                out.add(tileY(x, k, z));
    }

    private Quad planeZOutline(int k) {
        return new Quad(Kind.Z, k, 0, 0, k, map.size.widthX, 0, k, map.size.widthX, map.size.heightY, k, 0, map.size.heightY, k);
    }

    private Quad planeXOutline(int k) {
        return new Quad(Kind.X, k, k, 0, 0, k, map.size.heightY, 0, k, map.size.heightY, map.size.depthZ, k, 0, map.size.depthZ);
    }

    private Quad planeYOutline(int k) {
        return new Quad(Kind.Y, k, 0, k, 0, map.size.widthX, k, 0, map.size.widthX, k, map.size.depthZ, 0, k, map.size.depthZ);
    }

    private TileQuad tileZ(int x, int y, int z) {
        return new TileQuad(Kind.Z, z, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, x, y);
    }

    private TileQuad tileX(int x, int y, int z) {
        return new TileQuad(Kind.X, x, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1, y, z);
    }

    private TileQuad tileY(int x, int y, int z) {
        return new TileQuad(Kind.Y, y, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, x, z);
    }

    private void drawTileHighlight(GraphicsContext g, double[][] R, double scale, double ox, double oy, int x, int y, int z) {
        double[] s0 = project(R, scale, ox, oy, x, y, z);
        double[] s1 = project(R, scale, ox, oy, x + 1, y, z);
        double[] s2 = project(R, scale, ox, oy, x + 1, y + 1, z);
        double[] s3 = project(R, scale, ox, oy, x, y + 1, z);
        g.setFill(Color.color(1, 0.8, 0.0, 0.25));
        g.fillPolygon(new double[]{s0[0], s1[0], s2[0], s3[0]}, new double[]{s0[1], s1[1], s2[1], s3[1]}, 4);
        g.setStroke(Color.color(1, 0.8, 0.0, 0.95));
        g.setLineWidth(2.0);
        g.strokePolygon(new double[]{s0[0], s1[0], s2[0], s3[0]}, new double[]{s0[1], s1[1], s2[1], s3[1]}, 4);
    }
}
