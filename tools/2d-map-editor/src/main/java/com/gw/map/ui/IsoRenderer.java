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
 * True 3D-ish renderer:
 * - Every tile is a quad in world space: (x,y,z) to (x+1,y+1,z) etc.
 * - Camera uses yaw/pitch (roll supported if MapDef has it)
 * - Orthographic projection after rotation; depth-sorted quads to avoid obvious overdraw errors.
 * - In Tile Selection mode: only the selected plane is rendered; one tile is highlighted.
 */
public class IsoRenderer {

    private final Canvas canvas;
    // Base visual scale (pixels per world unit) before zoom
    private final double baseScale = 32.0;
    private MapDef map;
    private TextureResolver textureResolver = new DefaultTextureResolver();
    private TemplateRepository templateRepo = new TemplateRepository();

    public IsoRenderer(Canvas canvas) {
        this.canvas = canvas;
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

    // ------- Public API -------

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

    private static boolean pointInQuad(double sx, double sy, double[] a, double[] b, double[] c, double[] d) {
        return pointInTri(sx, sy, a, b, c) || pointInTri(sx, sy, a, c, d);
    }

    // ------- Internal: build plane/tile quads in world space (unit tiles) -------

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

    public void setMap(MapDef map) {
        this.map = map;
    }

    public void render(GraphicsContext g, MapDef map, SelectionState sel, boolean tileMode, String ghostTemplateId, double[] ghostXY) {
        // Clear
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (map == null) return;

        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;  // origin/pan
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;

        // Build rotation matrix from yaw (Y), pitch (X), roll (Z)
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);
        drawAxes(g, R, scale, ox, oy, map);
        drawPlanePlacements(g, map, R, scale, ox, oy);

        // Collect quads to draw (depth sort helps overlapping)
        List<Quad> quads = new ArrayList<>();

        if (tileMode) {
            // Only selected plane
            switch (sel.base) {
                case Z -> addPlaneZ(quads, sel.index, false);
                case X -> addPlaneX(quads, sel.index, false);
                case Y -> addPlaneY(quads, sel.index, false);
            }
        } else {
            // All planes
            for (int k = 0; k < map.size.depthZ; k++) addPlaneZ(quads, k, true);
            for (int k = 0; k < map.size.widthX; k++) addPlaneX(quads, k, true);
            for (int k = 0; k < map.size.heightY; k++) addPlaneY(quads, k, true);
        }

        // Depth sort: far (greater transformed z) first -> draw back-to-front
        quads.sort(Comparator.comparingDouble(q -> -q.avgZ));

        // Draw
        for (Quad q : quads) {
            // Highlight selected plane with stronger lines
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

        // Tile highlight (single) in tile mode
        if (tileMode && !sel.selectedTiles.isEmpty()) {
            var tk = sel.selectedTiles.iterator().next();
            // Convert plane-local (a,b) to world-space cell depending on base
            switch (sel.base) {
                case Z -> drawTileHighlight(g, R, scale, ox, oy, tk.a, tk.b, sel.index);
                case X -> drawTileHighlight(g, R, scale, ox, oy, sel.index, tk.a, tk.b);
                case Y -> drawTileHighlight(g, R, scale, ox, oy, tk.a, sel.index, tk.b);
            }
        }
    }

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

    private void drawPlanePlacements(GraphicsContext g, MapDef map, double[][] R, double scale, double ox, double oy) {
        if (map.planes == null || map.planes.isEmpty()) return;
        g.save();
        for (var entry : map.planes.entrySet()) {
            Plane2DMap plane = entry.getValue();
            if (plane == null || plane.placements.isEmpty()) continue;

            for (Plane2DMap.Placement p : plane.placements) {
                TemplateDef snap = p.dataSnap;
                if (snap == null) {
                    // try rehydrate lazily
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

                // choose source rect (first frame if animated)
                int sx = p.srcXpx, sy = p.srcYpx, sw = p.srcWpx, sh = p.srcHpx;
                if (snap.complex && snap.animated && snap.regions != null && !snap.regions.isEmpty()) {
                    int[] r = snap.pixelRegions().get(0);
                    sx = r[0];
                    sy = r[1];
                    sw = r[2];
                    sh = r[3];
                }

                // world placement origin + axes per plane
                int wTiles = Math.max(1, (int) Math.ceil(p.wTiles * p.scale));
                int hTiles = Math.max(1, (int) Math.ceil(p.hTiles * p.scale));

                double x0 = 0, y0 = 0, z0 = 0;
                // basis vectors for 1 tile steps on this plane (in world)
                double uxX = 0, uxY = 0, uxZ = 0;  // +U step
                double vyX = 0, vyY = 0, vyZ = 0;  // +V step

                switch (plane.base) {
                    case Z -> { // plane z = k, U along +X, V along +Y
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
                    case X -> { // plane x = k, U along +Y, V along +Z
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
                    case Y -> { // plane y = k, U along +X, V along +Z
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

                // screen origin S0 and per-tile screen vectors U,V
                double[] S0 = project(R, scale, ox, oy, x0, y0, z0);
                double[] SU = project(R, scale, ox, oy, x0 + uxX, y0 + uxY, z0 + uxZ);
                double[] SV = project(R, scale, ox, oy, x0 + vyX, y0 + vyY, z0 + vyZ);
                double Ux = SU[0] - S0[0], Uy = SU[1] - S0[1];
                double Vx = SV[0] - S0[0], Vy = SV[1] - S0[1];

                // per-pixel basis (affine) using template tile pixel sizes
                double perPxUx = Ux / Math.max(1.0, snap.tileWidthPx);
                double perPxUy = Uy / Math.max(1.0, snap.tileWidthPx);
                double perPxVx = Vx / Math.max(1.0, snap.tileHeightPx);
                double perPxVy = Vy / Math.max(1.0, snap.tileHeightPx);

                g.save();
                // build affine matrix to map source pixel coords to screen
                // [ mxx  mxy  tx ]
                // [ myx  myy  ty ]
                // pixel (x,y) -> S0 + x*U_per_px + y*V_per_px
                javafx.scene.transform.Affine A = new javafx.scene.transform.Affine(perPxUx, perPxUy, S0[0], perPxVx, perPxVy, S0[1]);
                g.setTransform(A);
                // draw the source rect at (0,0) sized sw x sh (in pixels of source)
                g.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
                g.restore();
            }
        }
        g.restore();
    }

    /**
     * Plane picking: iterate quads and return the first plane hit (based on mouse inside quad).
     */
    public PlaneHit hitTestPlane(double sx, double sy) {
        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        // Build ALL plane quads coarse (one quad per plane, not per tile) for picking outline
        List<Quad> planes = new ArrayList<>();
        // Z planes as big rectangles
        for (int k = 0; k < map.size.depthZ; k++) {
            planes.add(planeZOutline(k));
        }
        // X planes
        for (int k = 0; k < map.size.widthX; k++) {
            planes.add(planeXOutline(k));
        }
        // Y planes
        for (int k = 0; k < map.size.heightY; k++) {
            planes.add(planeYOutline(k));
        }

        // Sort front to back and return first that contains point
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

    /**
     * Tile picking on the selected plane: iterate tiles on that plane and test quad hit.
     */
    public int[] screenToPlaneTile(SelectionState.BasePlane base, int index, double sx, double sy) {
        double scale = baseScale * map.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + map.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + map.cameraPanY;
        double[][] R = rotationMatrix(map.cameraYawDeg, map.cameraPitchDeg, map.cameraRollDeg);

        // Tiles on that plane
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
        // Sort front to back and return first containing the point
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
        // Large quad spanning the whole Z=k plane in world coords
        double x0 = 0, y0 = 0, z0 = k;
        double x1 = map.size.widthX, y1 = 0, z1 = k;
        double x2 = map.size.widthX, y2 = map.size.heightY, z2 = k;
        double x3 = 0, y3 = map.size.heightY, z3 = k;
        return new Quad(Kind.Z, k, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    // ------- Math: rotation and projection -------

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
        // Z=k: tile spans (x,y,k) → (x+1,y+1,k)
        return new TileQuad(Kind.Z, z, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, x, y);
    }

    private TileQuad tileX(int x, int y, int z) {
        // X=k: tile spans (k,y,z) → (k,y+1,z+1)
        return new TileQuad(Kind.X, x, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1, y, z);
    }

    private TileQuad tileY(int x, int y, int z) {
        // Y=k: tile spans (x,k,z) → (x+1,k,z+1)
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

    public void setTextureResolver(TextureResolver r) {
        if (r != null) this.textureResolver = r;
    }

    public void setTemplateRepository(TemplateRepository repo) {
        if (repo != null) this.templateRepo = repo;
    }
}
