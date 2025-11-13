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
import javafx.scene.transform.Affine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * True 3D-ish renderer:
 * - Every tile is a quad in world space.
 * - Camera uses yaw/pitch/roll; orthographic projection.
 * - Depth-sorted quads for planes/tiles.
 * - Plane placements:
 * - Use Plane2DMap.Placement (including rotQ and scale).
 * - Animated templates: frames are advanced by time (like PlaneCanvasPane).
 * - Placements are stuck to their plane tiles in world space.
 * - In Tile Selection mode:
 * - Only the selected plane is rendered.
 * - Only placements on that plane are rendered.
 * - Single selected tile is highlighted.
 */
public class IsoRenderer {

    private final Canvas canvas;
    // Base visual scale (pixels per world unit) before camera zoom
    private final double baseScale = 32.0;

    private boolean showGrid = true;

    private MapDef map;
    private TextureResolver textureResolver = new DefaultTextureResolver();
    private TemplateRepository templateRepo = new TemplateRepository();

    public IsoRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }
    // R = Rz(roll) * Rx(pitch) * Ry(yaw)
    private static double[][] rotationMatrix(double yawDeg, double pitchDeg, double rollDeg) {
        double cy = Math.cos(Math.toRadians(yawDeg));
        double sy = Math.sin(Math.toRadians(yawDeg));
        double cx = Math.cos(Math.toRadians(pitchDeg));
        double sx = Math.sin(Math.toRadians(pitchDeg));
        double cz = Math.cos(Math.toRadians(rollDeg));
        double sz = Math.sin(Math.toRadians(rollDeg));

        // Ry
        double[][] Ry = new double[][]{{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        // Rx
        double[][] Rx = new double[][]{{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        // Rz
        double[][] Rz = new double[][]{{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};
        return mul(Rz, mul(Rx, Ry));
    }

    private static double[][] mul(double[][] A, double[][] B) {
        double[][] R = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                R[i][j] = A[i][0] * B[0][j] + A[i][1] * B[1][j] + A[i][2] * B[2][j];
        return R;
    }

    // Orthographic projection after rotation; screen y goes down, so invert y
    private static double[] project(double[][] R, double scale, double ox, double oy, double x, double y, double z) {
        double xr = R[0][0] * x + R[0][1] * y + R[0][2] * z;
        double yr = R[1][0] * x + R[1][1] * y + R[1][2] * z;
        double zr = R[2][0] * x + R[2][1] * y + R[2][2] * z;
        double sx = ox + xr * scale;
        double sy = oy - yr * scale;
        return new double[]{sx, sy, zr};
    }

    /* ============================================================
     *  Public entry point
     * ============================================================ */

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

    /* ============================================================
     *  Camera math
     * ============================================================ */

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

    /**
     * Render the whole map.
     *
     * @param g               graphics context of the canvas
     * @param map             current map
     * @param sel             selection state (plane + tile)
     * @param tileMode        true = tile selection mode, false = plane selection mode
     * @param ghostTemplateId (reserved for future ghost overlay)
     * @param ghostScreenPos  (reserved for future ghost overlay)
     */
    public void render(GraphicsContext g, MapDef map, SelectionState sel, boolean tileMode, String ghostTemplateId, double[] ghostScreenPos) {

        this.map = map;
        // clear
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (map == null) return;

        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;

        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        // Axes always shown (independent of grid toggle)
        drawAxes(g, R, scale, ox, oy, map);

        // Placements are independent of grid toggle
        drawPlanePlacements(g, map, sel, tileMode, R, scale, ox, oy);

        // ------- GRID (planes & tile lines) guarded by showGrid -------
        if (showGrid) {
            java.util.List<Quad> quads = new java.util.ArrayList<>();

            if (tileMode) {
                switch (sel.base) {
                    case Z -> addPlaneZ(quads, sel.index, false);
                    case X -> addPlaneX(quads, sel.index, false);
                    case Y -> addPlaneY(quads, sel.index, false);
                }
            } else {
                for (int k = 0; k < map.size.depthZ; k++) addPlaneZ(quads, k, true);
                for (int k = 0; k < map.size.widthX; k++) addPlaneX(quads, k, true);
                for (int k = 0; k < map.size.heightY; k++) addPlaneY(quads, k, true);
            }

            quads.sort(java.util.Comparator.comparingDouble(q -> -q.avgZ));

            for (Quad q : quads) {
                boolean highlightPlane =
                    (!tileMode) &&
                        ((q.kind == Kind.Z && q.index == (sel.base == SelectionState.BasePlane.Z ? sel.index : -1)) ||
                            (q.kind == Kind.X && q.index == (sel.base == SelectionState.BasePlane.X ? sel.index : -1)) ||
                            (q.kind == Kind.Y && q.index == (sel.base == SelectionState.BasePlane.Y ? sel.index : -1)));

                Color line = highlightPlane
                    ? Color.rgb(0, 128, 255, 0.9)
                    : Color.rgb(0, 0, 0, 0.12);

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
                g.fillPolygon(
                    new double[]{s0[0], s1[0], s2[0], s3[0]},
                    new double[]{s0[1], s1[1], s2[1], s3[1]},
                    4
                );
                g.setStroke(line);
                g.setLineWidth(highlightPlane ? 2.0 : 1.0);
                g.strokePolygon(
                    new double[]{s0[0], s1[0], s2[0], s3[0]},
                    new double[]{s0[1], s1[1], s2[1], s3[1]},
                    4
                );
            }
        }

        // Tile highlight still drawn even if grid hidden
        if (tileMode && !sel.selectedTiles.isEmpty()) {
            var tk = sel.selectedTiles.iterator().next();
            switch (sel.base) {
                case Z -> drawTileHighlight(g, R, scale, ox, oy, tk.a, tk.b, sel.index);
                case X -> drawTileHighlight(g, R, scale, ox, oy, sel.index, tk.a, tk.b);
                case Y -> drawTileHighlight(g, R, scale, ox, oy, tk.a, sel.index, tk.b);
            }
        }
    }

    /* ============================================================
     *  Axes
     * ============================================================ */

    private void drawAxes(GraphicsContext g, double[][] R, double scale, double ox, double oy, MapDef map) {
        double lx = Math.max(1, map.size.widthX);
        double ly = Math.max(1, map.size.heightY);
        double lz = Math.max(1, map.size.depthZ);

        double[] O = project(R, scale, ox, oy, 0, 0, 0);
        double[] Xp = project(R, scale, ox, oy, lx, 0, 0);
        double[] Yp = project(R, scale, ox, oy, 0, ly, 0);
        double[] Zp = project(R, scale, ox, oy, 0, 0, lz);

        g.setLineWidth(2.0);

        // X axis (red)
        g.setStroke(Color.rgb(220, 40, 40, 0.9));
        g.strokeLine(O[0], O[1], Xp[0], Xp[1]);
        g.setFill(Color.rgb(220, 40, 40, 0.95));
        g.fillText("X", Xp[0] + 6, Xp[1] - 6);

        // Y axis (green)
        g.setStroke(Color.rgb(40, 180, 60, 0.9));
        g.strokeLine(O[0], O[1], Yp[0], Yp[1]);
        g.setFill(Color.rgb(40, 180, 60, 0.95));
        g.fillText("Y", Yp[0] + 6, Yp[1] - 6);

        // Z axis (blue)
        g.setStroke(Color.rgb(40, 100, 220, 0.9));
        g.strokeLine(O[0], O[1], Zp[0], Zp[1]);
        g.setFill(Color.rgb(40, 100, 220, 0.95));
        g.fillText("Z", Zp[0] + 6, Zp[1] - 6);
    }

    /* ============================================================
     *  Plane / placement drawing (with rotation + animation)
     * ============================================================ */

    private void drawPlanePlacements(GraphicsContext g, MapDef map, SelectionState sel, boolean tileMode, double[][] R, double scale, double ox, double oy) {
        if (map.planes == null || map.planes.isEmpty()) return;

        g.save();
        for (String key : map.planes.keySet()) {
            Plane2DMap plane = map.planes.get(key);
            if (plane == null || plane.placements.isEmpty()) continue;

            // In tile-mode: only selected plane
            if (tileMode) {
                Plane2DMap.Base base = plane.base;
                if (!isSameBase(base, sel.base) || plane.planeIndex != sel.index) continue;
            }

            for (Plane2DMap.Placement p : plane.placements) {
                drawPlanePlacement(g, plane, p, R, scale, ox, oy);
            }
        }
        g.restore();
    }

    private boolean isSameBase(Plane2DMap.Base b, SelectionState.BasePlane sb) {
        return switch (sb) {
            case X -> b == Plane2DMap.Base.X;
            case Y -> b == Plane2DMap.Base.Y;
            case Z -> b == Plane2DMap.Base.Z;
        };
    }

    /**
     * Draw a single placement on its plane, using:
     * - p.rotQ (0,1,2,3) for rotation
     * - p.scale for scale in tile units
     * - animated frame if template is animated
     */
    private void drawPlanePlacement(GraphicsContext g, Plane2DMap plane, Plane2DMap.Placement p, double[][] R, double scale, double ox, double oy) {

        // Ensure snapshot
        TemplateDef snap = p.dataSnap;
        if (snap == null) {
            TemplateDef src = templateRepo.findById(p.templateId);
            if (src == null) return;
            if (p.regionIndex >= 0) {
                snap = TemplateSlice.copyRegion(src, Math.max(0, p.srcXpx / Math.max(1, src.tileWidthPx)), Math.max(0, p.srcYpx / Math.max(1, src.tileHeightPx)), Math.max(0, (p.srcXpx + p.srcWpx - 1) / Math.max(1, src.tileWidthPx)), Math.max(0, (p.srcYpx + p.srcHpx - 1) / Math.max(1, src.tileHeightPx)));
            } else {
                snap = TemplateSlice.copyWhole(src);
            }
            p.dataSnap = snap;
        }
        if (snap == null) return;

        // Texture
        Image img = loadTextureByLogicalPath(snap.logicalPath);
        if (img == null) {
            TemplateDef src = templateRepo.findById(p.templateId);
            if (src != null) img = loadTextureByLogicalPath(src.logicalPath);
        }
        if (img == null) return;

        // Source rect (animated frame or static)
        int sx = p.srcXpx, sy = p.srcYpx, sw = p.srcWpx, sh = p.srcHpx;
        if (isAnimated(snap)) {
            var frames = snap.pixelRegions();
            int idx = animatedFrameIndex(frames.size());
            int[] r = frames.get(Math.max(0, Math.min(idx, frames.size() - 1)));
            sx = r[0];
            sy = r[1];
            sw = r[2];
            sh = r[3];
        }

        // Base footprint in tiles from placement (unscaled)
        int baseW = Math.max(1, p.wTiles);
        int baseH = Math.max(1, p.hTiles);
        // Rotated footprint
        boolean swap = (p.rotQ & 1) == 1;
        int rotBaseW = swap ? baseH : baseW;
        int rotBaseH = swap ? baseW : baseH;

        double wTiles = Math.max(1, Math.ceil(rotBaseW * p.scale));
        double hTiles = Math.max(1, Math.ceil(rotBaseH * p.scale));

        // Plane-local tile origin of placement (top-left of its bounding rect)
        double u0 = p.gx;
        double v0 = p.gy;

        // Corners of the axis-aligned bounding box in plane coordinates
        double TLu = u0, TLv = v0;
        double TRu = u0 + wTiles, TRv = v0;
        double BLu = u0, BLv = v0 + hTiles;
        double BRu = u0 + wTiles, BRv = v0 + hTiles;

        // Map src corners to plane corners depending on rotQ
        double P0u, P0v, P1u, P1v, P2u, P2v;
        switch (p.rotQ & 3) {
            case 1 -> { // 90° CW
                P0u = TRu;
                P0v = TRv;   // (0,0) -> TR
                P1u = BRu;
                P1v = BRv;   // (sw,0) -> BR
                P2u = TLu;
                P2v = TLv;   // (0,sh) -> TL
            }
            case 2 -> { // 180°
                P0u = BRu;
                P0v = BRv;   // (0,0) -> BR
                P1u = BLu;
                P1v = BLv;   // (sw,0) -> BL
                P2u = TRu;
                P2v = TRv;   // (0,sh) -> TR
            }
            case 3 -> { // 270° CW (90° CCW)
                P0u = BLu;
                P0v = BLv;   // (0,0) -> BL
                P1u = TLu;
                P1v = TLv;   // (sw,0) -> TL
                P2u = BRu;
                P2v = BRv;   // (0,sh) -> BR
            }
            default -> { // 0°
                P0u = TLu;
                P0v = TLv;   // (0,0) -> TL
                P1u = TRu;
                P1v = TRv;   // (sw,0) -> TR
                P2u = BLu;
                P2v = BLv;   // (0,sh) -> BL
            }
        }

        // Convert plane coords -> world -> screen for those three corners
        double[] S0p = planePointToScreen(plane, P0u, P0v, R, scale, ox, oy);
        double[] S1p = planePointToScreen(plane, P1u, P1v, R, scale, ox, oy);
        double[] S2p = planePointToScreen(plane, P2u, P2v, R, scale, ox, oy);

        // Build affine mapping src (x,y) -> screen
        double tx = S0p[0];
        double ty = S0p[1];
        double mxx = (S1p[0] - S0p[0]) / Math.max(1.0, sw);
        double myx = (S1p[1] - S0p[1]) / Math.max(1.0, sw);
        double mxy = (S2p[0] - S0p[0]) / Math.max(1.0, sh);
        double myy = (S2p[1] - S0p[1]) / Math.max(1.0, sh);

        g.save();
        Affine A = new Affine(mxx, mxy, tx, myx, myy, ty);
        g.setTransform(A);
        g.setImageSmoothing(false);
        g.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
        g.restore();
    }

    /**
     * Plane-local (u,v) to world (x,y,z) then to screen.
     */
    private double[] planePointToScreen(Plane2DMap plane, double u, double v, double[][] R, double scale, double ox, double oy) {
        double x, y, z;
        switch (plane.base) {
            case Z -> { // z = k, u->x, v->y
                x = u;
                y = v;
                z = plane.planeIndex;
            }
            case X -> { // x = k, u->y, v->z
                x = plane.planeIndex;
                y = u;
                z = v;
            }
            case Y -> { // y = k, u->x, v->z
                x = u;
                y = plane.planeIndex;
                z = v;
            }
            default -> {
                x = u;
                y = v;
                z = 0;
            }
        }
        double[] s = project(R, scale, ox, oy, x, y, z);
        return new double[]{s[0], s[1]};
    }

    /* ============================================================
     *  Picking helpers (same as before)
     * ============================================================ */

    public PlaneHit hitTestPlane(double sx, double sy) {
        if (map == null) return null;
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
            if (pointInQuad(sx, sy, s0, s1, s2, s3)) {
                return new PlaneHit(q.kind, q.index);
            }
        }
        return null;
    }

    public int[] screenToPlaneTile(SelectionState.BasePlane base, int index, double sx, double sy) {
        if (map == null) return null;
        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        List<TileQuad> tiles = new ArrayList<>();
        switch (base) {
            case Z -> {
                for (int y = 0; y < map.size.heightY; y++)
                    for (int x = 0; x < map.size.widthX; x++)
                        tiles.add(tileZ(x, y, index));
            }
            case X -> {
                for (int z = 0; z < map.size.depthZ; z++)
                    for (int y = 0; y < map.size.heightY; y++)
                        tiles.add(tileX(index, y, z));
            }
            case Y -> {
                for (int z = 0; z < map.size.depthZ; z++)
                    for (int x = 0; x < map.size.widthX; x++)
                        tiles.add(tileY(x, index, z));
            }
        }
        tiles.sort(Comparator.comparingDouble(t -> -t.avgZ));
        for (TileQuad tq : tiles) {
            double[] s0 = project(R, scale, ox, oy, tq.x0, tq.y0, tq.z0);
            double[] s1 = project(R, scale, ox, oy, tq.x1, tq.y1, tq.z1);
            double[] s2 = project(R, scale, ox, oy, tq.x2, tq.y2, tq.z2);
            double[] s3 = project(R, scale, ox, oy, tq.x3, tq.y3, tq.z3);
            if (pointInQuad(sx, sy, s0, s1, s2, s3)) {
                return new int[]{tq.a, tq.b};
            }
        }
        return null;
    }

    /* ============================================================
     *  Quads for planes/tiles
     * ============================================================ */

    private void addPlaneZ(List<Quad> out, int k, boolean faint) {
        for (int y = 0; y < map.size.heightY; y++)
            for (int x = 0; x < map.size.widthX; x++)
                out.add(tileZ(x, y, k));
    }

    private void addPlaneX(List<Quad> out, int k, boolean faint) {
        for (int z = 0; z < map.size.depthZ; z++)
            for (int y = 0; y < map.size.heightY; y++)
                out.add(tileX(k, y, z));
    }

    private void addPlaneY(List<Quad> out, int k, boolean faint) {
        for (int z = 0; z < map.size.depthZ; z++)
            for (int x = 0; x < map.size.widthX; x++)
                out.add(tileY(x, k, z));
    }

    private Quad planeZOutline(int k) {
        double x0 = 0, y0 = 0, z0 = k;
        double x1 = map.size.widthX, y1 = 0, z1 = k;
        double x2 = map.size.widthX, y2 = map.size.heightY, z2 = k;
        double x3 = 0, y3 = map.size.heightY, z3 = k;
        return new Quad(Kind.Z, k, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    private Quad planeXOutline(int k) {
        double x0 = k, y0 = 0, z0 = 0;
        double x1 = k, y1 = map.size.heightY, z1 = 0;
        double x2 = k, y2 = map.size.heightY, z2 = map.size.depthZ;
        double x3 = k, y3 = 0, z3 = map.size.depthZ;
        return new Quad(Kind.X, k, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    private Quad planeYOutline(int k) {
        double x0 = 0, y0 = k, z0 = 0;
        double x1 = map.size.widthX, y1 = k, z1 = 0;
        double x2 = map.size.widthX, y2 = k, z2 = map.size.depthZ;
        double x3 = 0, y3 = k, z3 = map.size.depthZ;
        return new Quad(Kind.Y, k, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
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

    /* ============================================================
     *  Animation + texture helpers (same semantics as PlaneCanvas)
     * ============================================================ */

    private boolean isAnimated(TemplateDef t) {
        return t != null && t.complex && t.animated && t.regions != null && !t.regions.isEmpty();
    }

    private static final long FRAME_MS = 120;  // match TemplateGallery

    private int animatedFrameIndex(int frames) {
        if (frames <= 0) return 0;
        long nowMs = System.nanoTime() / 1_000_000L;
        long idx = (nowMs / FRAME_MS) % frames;
        return (int) idx;
    }

    private Image loadTextureByLogicalPath(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return null;
        try {
            String url = textureResolver.resolve(logicalPath);
            return (url == null) ? null : new Image(url, false);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
