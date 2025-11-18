package com.gw.world.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.ui.CollisionShapes;
import com.gw.editor.util.TemplateGateUtils;
import com.gw.editor.util.TemplateSlice;
import com.gw.map.io.DefaultTextureResolver;
import com.gw.map.io.TextureResolver;
import com.gw.map.model.MapDef;
import com.gw.map.model.Plane2DMap;
import com.gw.map.model.SelectionState;
import com.gw.world.model.Quad;
import com.gw.world.model.SectionQuad;
import com.gw.world.model.TileQuad;
import com.gw.world.model.WorldDef;
import com.gw.world.model.WorldDef.SectionPlacement;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorldIsoRenderer {

    private static final long FRAME_MS = 120;

    private final Canvas canvas;
    private final double baseScale = 32.0;

    private WorldDef world;
    private TextureResolver textureResolver = new DefaultTextureResolver();
    private TemplateRepository templateRepo = new TemplateRepository();

    /**
     * Optional ghost section that is currently being dragged.
     */
    private GhostSection ghostSection = null;

    /**
     * Currently selected section (for highlight).
     */
    private SectionPlacement selectedSection = null;

    public WorldIsoRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    /** Overlays toggled from right sidebar. */
    private boolean showCollisionOverlay = false;
    private boolean showGateOverlay = false;

    /** Gate selected in right sidebar (may be null). */
    private WorldDef.GateEndpoint highlightedGate = null;

    public void setShowCollisionOverlay(boolean show) {
        this.showCollisionOverlay = show;
    }

    public void setShowGateOverlay(boolean show) {
        this.showGateOverlay = show;
    }

    public void setHighlightedGate(WorldDef.GateEndpoint ep) {
        this.highlightedGate = ep;
    }

    private static double[][] rotationMatrix(double yawDeg, double pitchDeg, double rollDeg) {
        double cy = Math.cos(Math.toRadians(yawDeg));
        double sy = Math.sin(Math.toRadians(yawDeg));
        double cx = Math.cos(Math.toRadians(pitchDeg));
        double sx = Math.sin(Math.toRadians(pitchDeg));
        double cz = Math.cos(Math.toRadians(rollDeg));
        double sz = Math.sin(Math.toRadians(rollDeg));

        double[][] Ry = new double[][]{{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        double[][] Rx = new double[][]{{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
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

    private static double[] project(double[][] R, double scale, double ox, double oy, double x, double y, double z) {
        double xr = R[0][0] * x + R[0][1] * y + R[0][2] * z;
        double yr = R[1][0] * x + R[1][1] * y + R[1][2] * z;
        double zr = R[2][0] * x + R[2][1] * y + R[2][2] * z;
        double sx = ox + xr * scale;
        double sy = oy - yr * scale;
        return new double[]{sx, sy, zr};
    }

    /* ------------ selection control ------------ */

    private static double[] planeUDir(Plane2DMap plane) {
        return switch (plane.base) {
            case Z -> new double[]{1, 0, 0};
            case X -> new double[]{0, 1, 0};
            case Y -> new double[]{1, 0, 0};
        };
    }

    /* ------------ ghost control ------------ */

    private static double[] planeVDir(Plane2DMap plane) {
        return switch (plane.base) {
            case Z -> new double[]{0, 1, 0};
            case X -> new double[]{0, 0, 1};
            case Y -> new double[]{0, 0, 1};
        };
    }

    private static double[] planeToWorld(Plane2DMap plane, SectionPlacement section, double u, double v) {
        // Local coordinates in section space (before rotation)
        double lx, ly, lz;
        switch (plane.base) {
            case Z -> { // z = k, u->x, v->y
                lx = u;
                ly = v;
                lz = plane.planeIndex;
            }
            case X -> { // x = k, u->y, v->z
                lx = plane.planeIndex;
                ly = u;
                lz = v;
            }
            case Y -> { // y = k, u->x, v->z
                lx = u;
                ly = plane.planeIndex;
                lz = v;
            }
            default -> {
                lx = u;
                ly = v;
                lz = 0;
            }
        }

        // Section rotation: we interpret rotXDeg/rotYDeg/rotZDeg as pitch/yaw/roll
        // and reuse the existing rotationMatrix(yaw, pitch, roll).
        double[][] Rsec = rotationMatrix(
            section.rotYDeg, // yaw   ← rotate around Y
            section.rotXDeg, // pitch ← rotate around X
            section.rotZDeg  // roll  ← rotate around Z
        );

        double xr = Rsec[0][0] * lx + Rsec[0][1] * ly + Rsec[0][2] * lz;
        double yr = Rsec[1][0] * lx + Rsec[1][1] * ly + Rsec[1][2] * lz;
        double zr = Rsec[2][0] * lx + Rsec[2][1] * ly + Rsec[2][2] * lz;

        // Translate to world
        double x = section.wx + xr;
        double y = section.wy + yr;
        double z = section.wz + zr;

        return new double[]{x, y, z};
    }

    private static double[] rotateAroundAxis(double[] v, double[] n, double cosA, double sinA) {
        double nx = n[0], ny = n[1], nz = n[2];
        double vx = v[0], vy = v[1], vz = v[2];

        double dot = nx * vx + ny * vy + nz * vz;
        double cx = ny * vz - nz * vy;
        double cy = nz * vx - nx * vz;
        double cz = nx * vy - ny * vx;

        double vx2 = vx * cosA + cx * sinA + nx * dot * (1 - cosA);
        double vy2 = vy * cosA + cy * sinA + ny * dot * (1 - cosA);
        double vz2 = vz * cosA + cz * sinA + nz * dot * (1 - cosA);

        return new double[]{vx2, vy2, vz2};
    }

    private static double[] normVec(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-6) return new double[]{0, 0, 1};
        return new double[]{v[0] / len, v[1] / len, v[2] / len};
    }

    /* ============================================================
     *  Public entry
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
     *  Camera / math helpers
     * ============================================================ */

    private static boolean pointInQuad(double sx, double sy, double[] a, double[] b, double[] c, double[] d) {
        return pointInTri(sx, sy, a, b, c) || pointInTri(sx, sy, a, c, d);
    }

    public void setWorld(WorldDef world) {
        this.world = world;
    }

    public void setTextureResolver(TextureResolver r) {
        if (r != null) this.textureResolver = r;
    }

    public void setTemplateRepository(TemplateRepository repo) {
        if (repo != null) this.templateRepo = repo;
    }

    public void setSelectedSection(SectionPlacement sp) {
        this.selectedSection = sp;
    }

    public GhostSection getGhostSection() {
        return ghostSection;
    }

    public void setGhostSection(GhostSection ghost) {
        this.ghostSection = ghost;
    }

    public void clearGhostSection() {
        this.ghostSection = null;
    }

    /* ============================================================
     *  Axes + world bounds
     * ============================================================ */

    public void render(GraphicsContext g) {
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (world == null) return;

        double scale = baseScale * world.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + world.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + world.cameraPanY;

        double[][] R = rotationMatrix(world.cameraYawDeg, world.cameraPitchDeg, world.cameraRollDeg);

        drawAxes(g, R, scale, ox, oy, world);
        drawWorldBounds(g, R, scale, ox, oy, world);
        drawSections(g, R, scale, ox, oy, world);

        // Selected section outline
        if (selectedSection != null && selectedSection.map != null && selectedSection.map.size != null) {
            drawSelectedSectionOutline(g, R, scale, ox, oy, selectedSection);
        }

        // Ghost overlay
        if (ghostSection != null && ghostSection.map != null && ghostSection.map.size != null) {
            drawGhostSection(g, R, scale, ox, oy, world, ghostSection);
        }
    }

    private void drawAxes(GraphicsContext g, double[][] R, double scale, double ox, double oy, WorldDef world) {
        double lx = Math.max(1, world.size.widthX);
        double ly = Math.max(1, world.size.heightY);
        double lz = Math.max(1, world.size.depthZ);

        double[] O = project(R, scale, ox, oy, 0, 0, 0);
        double[] Xp = project(R, scale, ox, oy, lx, 0, 0);
        double[] Yp = project(R, scale, ox, oy, 0, ly, 0);
        double[] Zp = project(R, scale, ox, oy, 0, 0, lz);

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

    private void drawWorldBounds(GraphicsContext g, double[][] R, double scale, double ox, double oy, WorldDef world) {
        double w = Math.max(1, world.size.widthX);
        double h = Math.max(1, world.size.heightY);
        double d = Math.max(1, world.size.depthZ);

        double[][] corners = new double[][]{{0, 0, 0}, {w, 0, 0}, {w, h, 0}, {0, h, 0}, {0, 0, d}, {w, 0, d}, {w, h, d}, {0, h, d}};

        double[][] sc = new double[8][];
        for (int i = 0; i < 8; i++) {
            sc[i] = project(R, scale, ox, oy, corners[i][0], corners[i][1], corners[i][2]);
        }

        g.setStroke(Color.rgb(0, 0, 0, 0.25));
        g.setLineWidth(1.2);

        drawLine(g, sc[0], sc[1]);
        drawLine(g, sc[1], sc[2]);
        drawLine(g, sc[2], sc[3]);
        drawLine(g, sc[3], sc[0]);

        drawLine(g, sc[4], sc[5]);
        drawLine(g, sc[5], sc[6]);
        drawLine(g, sc[6], sc[7]);
        drawLine(g, sc[7], sc[4]);

        drawLine(g, sc[0], sc[4]);
        drawLine(g, sc[1], sc[5]);
        drawLine(g, sc[2], sc[6]);
        drawLine(g, sc[3], sc[7]);
    }

    /* ============================================================
     *  Sections & placements
     * ============================================================ */

    private void drawLine(GraphicsContext g, double[] a, double[] b) {
        g.strokeLine(a[0], a[1], b[0], b[1]);
    }

    private void drawSections(GraphicsContext g, double[][] R, double scale, double ox, double oy, WorldDef world) {
        if (world.sections == null || world.sections.isEmpty()) return;

        List<DrawItem> items = new ArrayList<>();
        for (SectionPlacement sp : world.sections) {
            if (sp == null || sp.map == null || sp.map.planes == null) continue;

            for (var e : sp.map.planes.entrySet()) {
                String planeKey = e.getKey();
                Plane2DMap plane = e.getValue();
                if (plane == null || plane.placements.isEmpty()) continue;

                for (Plane2DMap.Placement p : plane.placements) {
                    double depth = placementDepthCamera(sp, plane, p, R);
                    items.add(new DrawItem(sp, planeKey, plane, p, depth));
                }
            }
        }

        items.sort(Comparator.comparingDouble(di -> -di.depthZ));
        for (DrawItem di : items) {
            drawPlacement(g, di.section, di.planeKey, di.plane, di.p, R, scale, ox, oy);
        }
    }

    private double placementDepthCamera(SectionPlacement section, Plane2DMap plane, Plane2DMap.Placement p, double[][] R) {

        int baseW = Math.max(1, p.wTiles);
        int baseH = Math.max(1, p.hTiles);
        boolean swap = (p.rotQ & 1) == 1;
        double wTiles = Math.max(1, Math.ceil((swap ? baseH : baseW) * p.scale));
        double hTiles = Math.max(1, Math.ceil((swap ? baseW : baseH) * p.scale));

        double uCenter = p.gx + wTiles * 0.5;
        double vCenter = p.gy + hTiles * 0.5;

        double[] worldPos = planeToWorld(plane, section, uCenter, vCenter);
        double x = worldPos[0], y = worldPos[1], z = worldPos[2];

        double zr = R[2][0] * x + R[2][1] * y + R[2][2] * z;
        return zr;
    }

    private void drawPlacement(GraphicsContext g,
                               SectionPlacement section,
                               String planeKey,
                               Plane2DMap plane,
                               Plane2DMap.Placement p,
                               double[][] R, double scale, double ox, double oy){
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

        Image img = loadTextureByLogicalPath(snap.logicalPath);
        if (img == null) {
            TemplateDef src = templateRepo.findById(p.templateId);
            if (src != null) img = loadTextureByLogicalPath(src.logicalPath);
        }
        if (img == null) return;

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

        int baseW = Math.max(1, p.wTiles);
        int baseH = Math.max(1, p.hTiles);
        boolean swap = (p.rotQ & 1) == 1;
        int rotBaseW = swap ? baseH : baseW;
        int rotBaseH = swap ? baseW : baseH;

        double wTiles = Math.max(1, Math.ceil(rotBaseW * p.scale));
        double hTiles = Math.max(1, Math.ceil(rotBaseH * p.scale));

        double u0 = p.gx;
        double v0 = p.gy;

        double TLu = u0, TLv = v0;
        double TRu = u0 + wTiles, TRv = v0;
        double BLu = u0, BLv = v0 + hTiles;
        double BRu = u0 + wTiles, BRv = v0 + hTiles;

        double P0u, P0v, P1u, P1v, P2u, P2v;
        switch (p.rotQ & 3) {
            case 1 -> {
                P0u = TRu;
                P0v = TRv;
                P1u = BRu;
                P1v = BRv;
                P2u = TLu;
                P2v = TLv;
            }
            case 2 -> {
                P0u = BRu;
                P0v = BRv;
                P1u = BLu;
                P1v = BLv;
                P2u = TRu;
                P2v = TRv;
            }
            case 3 -> {
                P0u = BLu;
                P0v = BLv;
                P1u = TLu;
                P1v = TLv;
                P2u = BRu;
                P2v = BRv;
            }
            default -> {
                P0u = TLu;
                P0v = TLv;
                P1u = TRu;
                P1v = TRv;
                P2u = BLu;
                P2v = BLv;
            }
        }

        double[] P0w = planeToWorld(plane, section, P0u, P0v);
        double[] P1w = planeToWorld(plane, section, P1u, P1v);
        double[] P2w = planeToWorld(plane, section, P2u, P2v);

        double[] edge01 = new double[]{P1w[0] - P0w[0], P1w[1] - P0w[1], P1w[2] - P0w[2]};
        double[] edge02 = new double[]{P2w[0] - P0w[0], P2w[1] - P0w[1], P2w[2] - P0w[2]};
        double[] P3w = new double[]{P0w[0] + edge01[0] + edge02[0], P0w[1] + edge01[1] + edge02[1], P0w[2] + edge01[2] + edge02[2]};

        int mode = p.tiltMode;
        double ang = p.tiltDegrees;
        double angNorm = ((ang % 360.0) + 360.0) % 360.0;
        if (mode != 0 && Math.abs(angNorm) > 1e-3) {
            double[] uDir = planeUDir(plane);
            double[] vDir = planeVDir(plane);
            double[] axis;
            switch (mode) {
                case 1 -> axis = uDir;
                case 2 -> axis = vDir;
                case 3 -> axis = new double[]{uDir[0] + vDir[0], uDir[1] + vDir[1], uDir[2] + vDir[2]};
                case 4 -> axis = new double[]{uDir[0] - vDir[0], uDir[1] - vDir[1], uDir[2] - vDir[2]};
                default -> axis = new double[]{0, 0, 1};
            }
            axis = normVec(axis);

            double cx = (P0w[0] + P1w[0] + P2w[0] + P3w[0]) * 0.25;
            double cy = (P0w[1] + P1w[1] + P2w[1] + P3w[1]) * 0.25;
            double cz = (P0w[2] + P1w[2] + P2w[2] + P3w[2]) * 0.25;

            double rad = Math.toRadians(angNorm);
            double cosA = Math.cos(rad);
            double sinA = Math.sin(rad);

            P0w = rotateAroundAxis(new double[]{P0w[0] - cx, P0w[1] - cy, P0w[2] - cz}, axis, cosA, sinA);
            P1w = rotateAroundAxis(new double[]{P1w[0] - cx, P1w[1] - cy, P1w[2] - cz}, axis, cosA, sinA);
            P2w = rotateAroundAxis(new double[]{P2w[0] - cx, P2w[1] - cy, P2w[2] - cz}, axis, cosA, sinA);

            P0w[0] += cx;
            P0w[1] += cy;
            P0w[2] += cz;
            P1w[0] += cx;
            P1w[1] += cy;
            P1w[2] += cz;
            P2w[0] += cx;
            P2w[1] += cy;
            P2w[2] += cz;
        }

        double[] S0 = project(R, scale, ox, oy, P0w[0], P0w[1], P0w[2]);
        double[] S1 = project(R, scale, ox, oy, P1w[0], P1w[1], P1w[2]);
        double[] S2 = project(R, scale, ox, oy, P2w[0], P2w[1], P2w[2]);

        double tx = S0[0];
        double ty = S0[1];
        double mxx = (S1[0] - S0[0]) / Math.max(1.0, sw);
        double myx = (S1[1] - S0[1]) / Math.max(1.0, sw);
        double mxy = (S2[0] - S0[0]) / Math.max(1.0, sh);
        double myy = (S2[1] - S0[1]) / Math.max(1.0, sh);

        g.save();
        Affine A = new Affine(mxx, mxy, tx, myx, myy, ty);
        g.setTransform(A);
        g.setImageSmoothing(false);

        // Draw the texture region
        g.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);

        int tileW = snap.tileWidthPx;
        int tileH = snap.tileHeightPx;

        // ---------- BLUE highlight for currently selected gate (always active) ----------
        if (highlightedGate != null && world != null && world.sections != null
            && tileW > 0 && tileH > 0) {

            int thisSectionIndex = world.sections.indexOf(section);
            if (thisSectionIndex == highlightedGate.sectionIndex
                && planeKey != null
                && planeKey.equals(highlightedGate.planeKey)
                && highlightedGate.gateRef != null
                && highlightedGate.gateRef.pid() != null
                // IMPORTANT: compare to p.pid, not p.templateId
                && highlightedGate.gateRef.pid().equals(p.pid)) {

                var islands = TemplateGateUtils.computeGateIslands(snap);
                int gi = highlightedGate.gateRef.gateIndex();
                if (gi >= 0 && gi < islands.size()) {
                    var cluster = islands.get(gi);
                    for (int[] cell : cluster) {
                        int gx = cell[0];
                        int gy = cell[1];
                        double px = gx * tileW;
                        double py = gy * tileH;

                        g.setFill(Color.color(0.2, 0.5, 1.0, 0.35));
                        g.fillRect(px, py, tileW, tileH);
                        g.setStroke(Color.color(0.2, 0.5, 1.0, 0.95));
                        g.setLineWidth(2.5);
                        g.strokeRect(px, py, tileW, tileH);
                    }
                }
            }
        }

        // ---------- Optional overlays: collision (yellow) + gate tiles (green) ----------
        if ((showCollisionOverlay || showGateOverlay) && tileW > 0 && tileH > 0) {
            int tilesX = sw / tileW;
            int tilesY = sh / tileH;

            for (int tyTile = 0; tyTile < tilesY; tyTile++) {
                for (int txTile = 0; txTile < tilesX; txTile++) {
                    TemplateDef.TileDef td = snap.tileAt(txTile, tyTile);
                    if (td == null) continue;

                    double px = txTile * tileW;
                    double py = tyTile * tileH;

                    // Collision tiles (solid) in yellow via CollisionShapes.
                    if (showCollisionOverlay && td.solid) {
                        TemplateDef.ShapeType st =
                            (td.shape != null ? td.shape : TemplateDef.ShapeType.RECT_FULL);
                        TemplateDef.Orientation ori =
                            (td.orientation != null ? td.orientation : TemplateDef.Orientation.UP);
                        CollisionShapes.get(st).draw(g, px, py, tileW, tileH, ori);
                    }

                    // Gate tiles in green.
                    if (showGateOverlay && td.gate) {
                        g.setFill(Color.color(0, 1, 0, 0.25));
                        g.fillRect(px, py, tileW, tileH);
                        g.setStroke(Color.color(0, 1, 0, 0.9));
                        g.setLineWidth(2);
                        g.strokeRect(px, py, tileW, tileH);
                    }
                }
            }
        }

        g.restore();
    }

    /* ============================================================
     *  Section outlines: selected + ghost
     * ============================================================ */

    private void drawSelectedSectionOutline(GraphicsContext g, double[][] R, double scale, double ox, double oy, SectionPlacement sp) {

        if (sp.map == null || sp.map.size == null) return;

        int sx = Math.max(1, sp.map.size.widthX);
        int sy = Math.max(1, sp.map.size.heightY);
        int sz = Math.max(1, sp.map.size.depthZ);

        // Local corners in section space (before rotation)
        double[][] local = new double[][]{
            {0,   0,   0},   // 0: (0,0,0)
            {sx,  0,   0},   // 1: (sx,0,0)
            {sx,  sy,  0},   // 2: (sx,sy,0)
            {0,   sy,  0},   // 3: (0,sy,0)
            {0,   0,   sz},  // 4: (0,0,sz)
            {sx,  0,   sz},  // 5: (sx,0,sz)
            {sx,  sy,  sz},  // 6: (sx,sy,sz)
            {0,   sy,  sz}   // 7: (0,sy,sz)
        };

        // Section rotation as a matrix (reuse camera helper)
        double[][] Rsec = rotationMatrix(
            sp.rotYDeg, // yaw around Y
            sp.rotXDeg, // pitch around X
            sp.rotZDeg  // roll around Z
        );

        // Rotate + translate corners into world space
        double[][] worldPts = new double[8][3];
        for (int i = 0; i < 8; i++) {
            double lx = local[i][0];
            double ly = local[i][1];
            double lz = local[i][2];

            double xr = Rsec[0][0] * lx + Rsec[0][1] * ly + Rsec[0][2] * lz;
            double yr = Rsec[1][0] * lx + Rsec[1][1] * ly + Rsec[1][2] * lz;
            double zr = Rsec[2][0] * lx + Rsec[2][1] * ly + Rsec[2][2] * lz;

            worldPts[i][0] = sp.wx + xr;
            worldPts[i][1] = sp.wy + yr;
            worldPts[i][2] = sp.wz + zr;
        }

        // Helper to create Quads
        List<Quad> faces = new ArrayList<>();

        // Indices: 0..7 as defined above
        double[] p000 = worldPts[0];
        double[] p100 = worldPts[1];
        double[] p110 = worldPts[2];
        double[] p010 = worldPts[3];
        double[] p001 = worldPts[4];
        double[] p101 = worldPts[5];
        double[] p111 = worldPts[6];
        double[] p011 = worldPts[7];

        // Same faces as before, just with rotated points
        // "bottom" (z ~ 0 in local space)
        faces.add(new Quad(
            p000[0], p000[1], p000[2],
            p100[0], p100[1], p100[2],
            p110[0], p110[1], p110[2],
            p010[0], p010[1], p010[2]
        ));
        // "top" (z ~ sz)
        faces.add(new Quad(
            p001[0], p001[1], p001[2],
            p101[0], p101[1], p101[2],
            p111[0], p111[1], p111[2],
            p011[0], p011[1], p011[2]
        ));
        // front
        faces.add(new Quad(
            p000[0], p000[1], p000[2],
            p100[0], p100[1], p100[2],
            p101[0], p101[1], p101[2],
            p001[0], p001[1], p001[2]
        ));
        // back
        faces.add(new Quad(
            p010[0], p010[1], p010[2],
            p110[0], p110[1], p110[2],
            p111[0], p111[1], p111[2],
            p011[0], p011[1], p011[2]
        ));
        // left
        faces.add(new Quad(
            p000[0], p000[1], p000[2],
            p010[0], p010[1], p010[2],
            p011[0], p011[1], p011[2],
            p001[0], p001[1], p001[2]
        ));
        // right
        faces.add(new Quad(
            p100[0], p100[1], p100[2],
            p110[0], p110[1], p110[2],
            p111[0], p111[1], p111[2],
            p101[0], p101[1], p101[2]
        ));

        faces.sort(Comparator.comparingDouble(q -> -q.avgZ));

        for (Quad q : faces) {
            double[] s0 = project(R, scale, ox, oy, q.x0, q.y0, q.z0);
            double[] s1 = project(R, scale, ox, oy, q.x1, q.y1, q.z1);
            double[] s2 = project(R, scale, ox, oy, q.x2, q.y2, q.z2);
            double[] s3 = project(R, scale, ox, oy, q.x3, q.y3, q.z3);

            g.setStroke(Color.rgb(255, 180, 0, 0.95));
            g.setLineWidth(3.0);
            g.strokePolygon(
                new double[]{s0[0], s1[0], s2[0], s3[0]},
                new double[]{s0[1], s1[1], s2[1], s3[1]},
                4
            );
        }
    }

    private void drawGhostSection(GraphicsContext g,
                                  double[][] R, double scale, double ox, double oy,
                                  WorldDef world, GhostSection ghost) {

        MapDef map = ghost.map;
        if (map == null || map.planes == null) return;

        // Synthetic SectionPlacement just for rendering
        SectionPlacement sp = new SectionPlacement(map.id, ghost.wx, ghost.wy, ghost.wz);
        sp.map = map;

        // 1) Draw the actual section content at ghost position (semi-transparent)
        List<DrawItem> items = new ArrayList<>();
        for (Plane2DMap plane : map.planes.values()) {
            if (plane == null || plane.placements == null || plane.placements.isEmpty()) continue;

            for (Plane2DMap.Placement p : plane.placements) {
                double depth = placementDepthCamera(sp, plane, p, R);
                items.add(new DrawItem(sp, plane.id, plane, p, depth));
            }
        }
        items.sort(Comparator.comparingDouble(di -> -di.depthZ));

        g.save();
        g.setGlobalAlpha(0.65); // ghost content slightly transparent
        for (DrawItem di : items) {
            drawPlacement(g, di.section, di.planeKey, di.plane, di.p, R, scale, ox, oy);
        }
        g.restore();

        // 2) Draw translucent bounding box around the ghost section
        int sx = Math.max(1, map.size.widthX);
        int sy = Math.max(1, map.size.heightY);
        int sz = Math.max(1, map.size.depthZ);

        double x0 = ghost.wx;
        double y0 = ghost.wy;
        double z0 = ghost.wz;
        double x1 = ghost.wx + sx;
        double y1 = ghost.wy + sy;
        double z1 = ghost.wz + sz;

        List<Quad> faces = new ArrayList<>();

        faces.add(new Quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0));
        faces.add(new Quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1));
        faces.add(new Quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1));
        faces.add(new Quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1));
        faces.add(new Quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1));
        faces.add(new Quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1));

        faces.sort(Comparator.comparingDouble(q -> -q.avgZ));

        for (Quad q : faces) {
            double[] s0 = project(R, scale, ox, oy, q.x0, q.y0, q.z0);
            double[] s1 = project(R, scale, ox, oy, q.x1, q.y1, q.z1);
            double[] s2 = project(R, scale, ox, oy, q.x2, q.y2, q.z2);
            double[] s3 = project(R, scale, ox, oy, q.x3, q.y3, q.z3);

            g.setFill(Color.rgb(0, 160, 255, 0.12));
            g.fillPolygon(
                new double[]{s0[0], s1[0], s2[0], s3[0]},
                new double[]{s0[1], s1[1], s2[1], s3[1]},
                4
            );

            g.setStroke(Color.rgb(0, 180, 255, 0.9));
            g.setLineWidth(2.0);
            g.strokePolygon(
                new double[]{s0[0], s1[0], s2[0], s3[0]},
                new double[]{s0[1], s1[1], s2[1], s3[1]},
                4
            );
        }
    }

    /* ============================================================
     *  Picking on Z=0 plane
     * ============================================================ */

    private TileQuad tileZ(int x, int y, int z) {
        double x0 = x, y0 = y, z0 = z;
        double x1 = x + 1, y1 = y, z1 = z;
        double x2 = x + 1, y2 = y + 1, z2 = z;
        double x3 = x, y3 = y + 1, z3 = z;
        return new TileQuad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, x, y);
    }

    private TileQuad tileX(int x, int y, int z) {
        double x0 = x, y0 = y,     z0 = z;
        double x1 = x, y1 = y + 1, z1 = z;
        double x2 = x, y2 = y + 1, z2 = z + 1;
        double x3 = x, y3 = y,     z3 = z + 1;

        // tile indices: (y,z)
        return new TileQuad(
            x0, y0, z0,
            x1, y1, z1,
            x2, y2, z2,
            x3, y3, z3,
            y,  z
        );
    }

    private TileQuad tileY(int x, int y, int z) {
        double x0 = x,     y0 = y, z0 = z;
        double x1 = x + 1, y1 = y, z1 = z;
        double x2 = x + 1, y2 = y, z2 = z + 1;
        double x3 = x,     y3 = y, z3 = z + 1;

        // tile indices: (x,z)
        return new TileQuad(
            x0, y0, z0,
            x1, y1, z1,
            x2, y2, z2,
            x3, y3, z3,
            x,  z
        );
    }

    public int[] screenToWorldTileOnZPlane(int zPlane, double sx, double sy) {
        // Backwards-compatible wrapper
        return screenToWorldTileOnPlane(SelectionState.BasePlane.Z, zPlane, sx, sy);
    }

    public int[] screenToWorldTileOnPlane(SelectionState.BasePlane base, int index,
                                          double sx, double sy) {
        if (world == null) return null;

        double scale = baseScale * world.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + world.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + world.cameraPanY;
        double[][] R = rotationMatrix(world.cameraYawDeg, world.cameraPitchDeg, world.cameraRollDeg);

        List<TileQuad> tiles = new ArrayList<>();

        switch (base) {
            case Z -> {
                // z = index, vary x,y
                for (int y = 0; y < world.size.heightY; y++) {
                    for (int x = 0; x < world.size.widthX; x++) {
                        tiles.add(tileZ(x, y, index));
                    }
                }
            }
            case X -> {
                // x = index, vary y,z
                for (int z = 0; z < world.size.depthZ; z++) {
                    for (int y = 0; y < world.size.heightY; y++) {
                        tiles.add(tileX(index, y, z));
                    }
                }
            }
            case Y -> {
                // y = index, vary x,z
                for (int z = 0; z < world.size.depthZ; z++) {
                    for (int x = 0; x < world.size.widthX; x++) {
                        tiles.add(tileY(x, index, z));
                    }
                }
            }
        }

        tiles.sort(Comparator.comparingDouble(t -> -t.avgZ));

        for (TileQuad tq : tiles) {
            double[] s0 = project(R, scale, ox, oy, tq.x0, tq.y0, tq.z0);
            double[] s1 = project(R, scale, ox, oy, tq.x1, tq.y1, tq.z1);
            double[] s2 = project(R, scale, ox, oy, tq.x2, tq.y2, tq.z2);
            double[] s3 = project(R, scale, ox, oy, tq.x3, tq.y3, tq.z3);
            if (pointInQuad(sx, sy, s0, s1, s2, s3)) {
                return new int[]{tq.tx, tq.ty};
            }
        }
        return null;
    }

    public SectionPlacement hitTestSection(double sx, double sy) {
        if (world == null || world.sections == null || world.sections.isEmpty()) return null;

        double scale = baseScale * world.cameraZoom;
        double ox = canvas.getWidth() * 0.5 + world.cameraPanX;
        double oy = canvas.getHeight() * 0.25 + world.cameraPanY;
        double[][] R = rotationMatrix(world.cameraYawDeg, world.cameraPitchDeg, world.cameraRollDeg);

        List<SectionQuad> quads = new ArrayList<>();

        for (SectionPlacement sp : world.sections) {
            if (sp == null || sp.map == null || sp.map.size == null) continue;

            int sxTiles = Math.max(1, sp.map.size.widthX);
            int syTiles = Math.max(1, sp.map.size.heightY);
            int szTiles = Math.max(1, sp.map.size.depthZ);

            double x0 = sp.wx;
            double y0 = sp.wy;
            double z0 = sp.wz;
            double x1 = sp.wx + sxTiles;
            double y1 = sp.wy + syTiles;
            double z1 = sp.wz + szTiles;

            // Same faces as outline/ghost
            quads.add(new SectionQuad(sp, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0)); // bottom
            quads.add(new SectionQuad(sp, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)); // top
            quads.add(new SectionQuad(sp, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)); // front
            quads.add(new SectionQuad(sp, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1)); // back
            quads.add(new SectionQuad(sp, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1)); // left
            quads.add(new SectionQuad(sp, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1)); // right
        }

        // Approx front-most: same convention as other picking (sort by -avgZ)
        quads.sort(Comparator.comparingDouble(q -> -q.avgZ));

        for (SectionQuad q : quads) {
            double[] s0 = project(R, scale, ox, oy, q.x0, q.y0, q.z0);
            double[] s1 = project(R, scale, ox, oy, q.x1, q.y1, q.z1);
            double[] s2 = project(R, scale, ox, oy, q.x2, q.y2, q.z2);
            double[] s3 = project(R, scale, ox, oy, q.x3, q.y3, q.z3);

            if (pointInQuad(sx, sy, s0, s1, s2, s3)) {
                return q.section;
            }
        }
        return null;
    }

    private boolean isAnimated(TemplateDef t) {
        return t != null && t.complex && t.animated && t.regions != null && !t.regions.isEmpty();
    }

    private int animatedFrameIndex(int frames) {
        if (frames <= 0) return 0;
        long nowMs = System.nanoTime() / 1_000_000L;
        long idx = (nowMs / FRAME_MS) % frames;
        return (int) idx;
    }

    /* ============================================================
     *  Animation + texture
     * ============================================================ */

    private Image loadTextureByLogicalPath(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return null;
        try {
            String url = textureResolver.resolve(logicalPath);
            System.out.println("url: " + url );
            return (url == null) ? null : new Image(url, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class GhostSection {
        public MapDef map;
        public int wx, wy, wz;

        public GhostSection() {
        }

        public GhostSection(MapDef map, int wx, int wy, int wz) {
            this.map = map;
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
        }
    }

    private record DrawItem(
        SectionPlacement section,
        String planeKey,
        Plane2DMap plane,
        Plane2DMap.Placement p,
        double depthZ
    ) {}
}
