package com.gw.editor.ui;

import com.gw.editor.map.MapDef;
import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateSlice;
import com.gwngames.core.api.asset.IAssetManager;
import javafx.animation.AnimationTimer;
import javafx.beans.property.*;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Map canvas with layers, grid, placements, live drop preview, zoom + selection & move + overlays.
 */
public class MapCanvasPane extends Region {
    public static final DataFormat DND_FORMAT = new DataFormat("application/x-gw-template-drop");

    private final Canvas canvas = new Canvas();
    private final GraphicsContext g = canvas.getGraphicsContext2D();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

    private final BooleanProperty showCollisions = new SimpleBooleanProperty(true);
    private final BooleanProperty showGates = new SimpleBooleanProperty(true);
    private final IntegerProperty currentLayer = new SimpleIntegerProperty(0);

    private final TemplateRepository templateRepo;
    private final IAssetManager manager;

    private MapDef map;
    private DropPreview preview = null;

    private final StringProperty selectedPid = new SimpleStringProperty(null);
    private boolean draggingInstance = false;
    private int dragOffsetGX = 0, dragOffsetGY = 0;
    private java.util.function.Consumer<MapDef.Placement> onSelectionChanged;

    // NEW: lightweight animation driver for animated placements
    private final AnimationTimer animTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            // Only redraw if we actually have something animated on screen.
            if (hasAnimatedPlacements()) redraw();
        }
    };

    public MapCanvasPane(TemplateRepository repo, IAssetManager manager) {
        this.templateRepo = repo;
        this.manager = manager;
        getChildren().add(canvas);

        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
        tileW.addListener((o, a, b) -> layoutForMap());
        tileH.addListener((o, a, b) -> layoutForMap());
        zoom.addListener((o, a, b) -> layoutForMap());
        currentLayer.addListener((o, a, b) -> requestLayout());

        setOnDragOver(this::onDragOver);
        setOnDragExited(e -> {
            preview = null;
            redraw();
        });
        setOnDragDropped(this::onDragDropped);

        setOnScroll(e -> {
            if (!e.isControlDown() && !e.isShortcutDown()) return;
            if (e.getDeltaY() > 0) zoomIn();
            else zoomOut();
            e.consume();
        });

        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);

        // NEW: Delete key to remove selected placement (with confirm)
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                deleteSelectedWithConfirm();
                e.consume();
            }
        });

        setFocusTraversable(true);

        // Start timer; it only repaints when animations exist.
        animTimer.start();
    }

    /* ---------- API ------------ */
    public IntegerProperty tileWidthProperty() {
        return tileW;
    }

    public IntegerProperty tileHeightProperty() {
        return tileH;
    }

    public DoubleProperty zoomProperty() {
        return zoom;
    }

    public StringProperty selectedPidProperty() {
        return selectedPid;
    }

    public BooleanProperty showCollisionsProperty() {
        return showCollisions;
    }

    public BooleanProperty showGatesProperty() {
        return showGates;
    }

    public IntegerProperty currentLayerProperty() {
        return currentLayer;
    }

    public void setOnSelectionChanged(java.util.function.Consumer<MapDef.Placement> cb) {
        this.onSelectionChanged = cb;
    }

    public boolean setMapSize(int w, int h) {
        if (map == null || w < 1 || h < 1) return false;
        boolean anyOut = map.placements.stream().anyMatch(p ->
            p.gx < 0 || p.gy < 0 ||
                p.gx + Math.max(1, (int) Math.ceil(p.wTiles * p.scale)) > w ||
                p.gy + Math.max(1, (int) Math.ceil(p.hTiles * p.scale)) > h);
        if (anyOut) return false;
        map.widthTiles = w;
        map.heightTiles = h;
        layoutForMap();
        return true;
    }

    public void zoomIn() {
        zoom.set(Math.min(4.0, zoom.get() * 1.25));
    }

    public void zoomOut() {
        zoom.set(Math.max(0.25, zoom.get() / 1.25));
    }

    public void zoomReset() {
        zoom.set(1.0);
    }

    public MapDef.Placement getSelected() {
        if (map == null || selectedPid.get() == null) return null;
        return map.placements.stream().filter(p -> p.pid.equals(selectedPid.get())).findFirst().orElse(null);
    }

    public void selectPid(String pid) {
        selectedPid.set(pid);
        if (onSelectionChanged != null) onSelectionChanged.accept(getSelected());
        redraw();
        requestFocus(); // so Delete works without extra click
    }

    /* ---------- DnD ---------- */
    private void onDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (!db.hasContent(DND_FORMAT)) return;

        String[] tok = ((String) db.getContent(DND_FORMAT)).split("\\|");
        if (tok.length < 9) return;

        String templateId = tok[0];
        int regionIndex = Integer.parseInt(tok[1]);
        int tW = Integer.parseInt(tok[2]);
        int tH = Integer.parseInt(tok[3]);
        int rpX = Integer.parseInt(tok[4]);
        int rpY = Integer.parseInt(tok[5]);
        int rpW = Integer.parseInt(tok[6]);
        int rpH = Integer.parseInt(tok[7]);
        double scaleMul = Double.parseDouble(tok[8]);

        double localX = e.getX() / zoom.get();
        double localY = e.getY() / zoom.get();
        int gx = (int) Math.floor(localX / tileW.get());
        int gy = (int) Math.floor(localY / tileH.get());
        int baseWTiles = Math.max(1, (int) Math.round((double) rpW / tW));
        int baseHTiles = Math.max(1, (int) Math.round((double) rpH / tH));
        int wTiles = Math.max(1, (int) Math.ceil(baseWTiles * Math.max(0.01, scaleMul)));
        int hTiles = Math.max(1, (int) Math.ceil(baseHTiles * Math.max(0.01, scaleMul)));

        if (map != null) {
            gx = Math.max(0, Math.min(gx, Math.max(0, map.widthTiles - wTiles)));
            gy = Math.max(0, Math.min(gy, Math.max(0, map.heightTiles - hTiles)));
        }

        Image texture = null;
        try {
            var td = templateRepo.findById(templateId);
            if (td != null) {
                String abs = manager.toAbsolute(td.logicalPath);
                texture = new Image(Path.of(abs).toUri().toString());
            }
        } catch (Exception ignored) {
        }

        preview = new DropPreview(templateId, regionIndex, gx, gy, wTiles, hTiles,
            tW, tH, rpX, rpY, rpW, rpH, scaleMul, texture);
        e.acceptTransferModes(TransferMode.COPY);
        e.consume();
        redraw();
    }

    private void onDragDropped(DragEvent e) {
        if (preview == null || map == null) {
            e.setDropCompleted(false);
            return;
        }

        var src = templateRepo.findById(preview.templateId);
        // Take a snapshot. For animations we want ALL frames (copyWhole); for static regions we slice.
        TemplateDef snap = (preview.regionIndex >= 0 && src != null && src.regions != null && preview.rpw > 0 && preview.rph > 0)
            ? TemplateSlice.copyRegion(src,
            preview.rpx / Math.max(1, preview.tW),
            preview.rpy / Math.max(1, preview.tH),
            preview.rpx / Math.max(1, preview.tW) + Math.max(1, (int)Math.round((double)preview.rpw / preview.tW)) - 1,
            preview.rpy / Math.max(1, preview.tH) + Math.max(1, (int)Math.round((double)preview.rph / preview.tH)) - 1)
            : TemplateSlice.copyWhole(src);

        // --- Compute base footprint in tiles ---
        // Default (static or simple): from snapshot image size
        int baseW = Math.max(1, snap.imageWidthPx  / Math.max(1, snap.tileWidthPx));
        int baseH = Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx));

        if (isAnimated(snap)) {
            int[] wh = firstFrameTilesWH(snap);
            baseW = wh[0]; baseH = wh[1];
        } else {
            baseW = Math.max(1, snap.imageWidthPx  / Math.max(1, snap.tileWidthPx));
            baseH = Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx));
        }

        // Animated: use FIRST FRAME ONLY for the base footprint
        if (snap.complex && snap.animated && snap.regions != null && !snap.regions.isEmpty()) {
            int[] first = snap.pixelRegions().getFirst(); // [x,y,w,h] in px
            baseW = Math.max(1, (int)Math.round((double) first[2] / Math.max(1, snap.tileWidthPx)));
            baseH = Math.max(1, (int)Math.round((double) first[3] / Math.max(1, snap.tileHeightPx)));
        }

        // Scale multiplier from preview
        double scl = preview.scaleMul <= 0 ? 1.0 : preview.scaleMul;

        // Final snapped placement footprint in tiles (kept on Placement)
        int wTiles = Math.max(1, (int)Math.ceil(baseW * scl));
        int hTiles = Math.max(1, (int)Math.ceil(baseH * scl));

        // Clamp drop origin so it fits the map
        int gx = Math.max(0, Math.min(preview.gx, map.widthTiles  - wTiles));
        int gy = Math.max(0, Math.min(preview.gy, map.heightTiles - hTiles));

        int layerIdx = Math.max(0, Math.min(currentLayer.get(), Math.max(0, map.layers.size() - 1)));

        MapDef.Placement p = new MapDef.Placement(
            preview.templateId, preview.regionIndex, gx, gy,
            /* base (unscaled) footprint to keep on the placement */
            baseW, baseH,
            /* src rect stays the first frame (for previewed region or whole anim) */
            preview.rpx, preview.rpy, preview.rpw, preview.rph,
            snap, layerIdx, scl
        );

        map.placements.add(p);
        preview = null;
        selectPid(p.pid);
        e.setDropCompleted(true);
        e.consume();
    }

    /* ---------- Selection & moving ---------- */
    private void onMousePressed(MouseEvent e) {
        if (map == null) return;
        double lx = e.getX() / zoom.get();
        double ly = e.getY() / zoom.get();
        int gx = (int) Math.floor(lx / tileW.get());
        int gy = (int) Math.floor(ly / tileH.get());
        MapDef.Placement hit = hitTestTopMost(gx, gy);
        if (hit != null) {
            selectPid(hit.pid);
            draggingInstance = true;
            dragOffsetGX = gx - hit.gx;
            dragOffsetGY = gy - hit.gy;
        } else {
            selectPid(null);
        }
        e.consume();
    }

    private void onMouseDragged(MouseEvent e) {
        if (!draggingInstance || map == null) return;
        MapDef.Placement sel = getSelected();
        if (sel == null) return;

        double lx = e.getX() / zoom.get();
        double ly = e.getY() / zoom.get();
        int gx = (int) Math.floor(lx / tileW.get()) - dragOffsetGX;
        int gy = (int) Math.floor(ly / tileH.get()) - dragOffsetGY;

        int wTiles = Math.max(1, (int) Math.ceil(sel.wTiles * sel.scale));
        int hTiles = Math.max(1, (int) Math.ceil(sel.hTiles * sel.scale));

        gx = Math.max(0, Math.min(gx, map.widthTiles - wTiles));
        gy = Math.max(0, Math.min(gy, map.heightTiles - hTiles));

        sel.gx = gx;
        sel.gy = gy;
        redraw();
        e.consume();
    }

    private void onMouseReleased(MouseEvent e) {
        draggingInstance = false;
        e.consume();
    }

    private MapDef.Placement hitTestTopMost(int gx, int gy) {
        if (map == null) return null;
        var ordered = map.placements.stream()
            .sorted(Comparator.comparingInt((MapDef.Placement p) -> p.layer)
                .thenComparingInt(map.placements::indexOf))
            .toList();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MapDef.Placement p = ordered.get(i);
            int[] wh = baseFootprintTiles(p);
            int wTiles = Math.max(1, (int)Math.ceil(wh[0] * p.scale));
            int hTiles = Math.max(1, (int)Math.ceil(wh[1] * p.scale));
            if (gx >= p.gx && gx < p.gx + wTiles && gy >= p.gy && gy < p.gy + hTiles) return p;
        }
        return null;
    }

    /* ---------- Layout & draw ---------- */
    private void layoutForMap() {
        double pixelW = (map == null ? 64 : map.widthTiles) * tileW.get();
        double pixelH = (map == null ? 36 : map.heightTiles) * tileH.get();
        canvas.setWidth(Math.max(32, pixelW * zoom.get()));
        canvas.setHeight(Math.max(32, pixelH * zoom.get()));
        redraw();
    }

    private void redraw() {
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setFill(Color.color(0.07, 0.07, 0.07));
        g.fillRect(0, 0, W, H);

        g.save();
        g.scale(zoom.get(), zoom.get());

        int mw = map == null ? 64 : map.widthTiles;
        int mh = map == null ? 36 : map.heightTiles;
        double mapPxW = mw * tileW.get(), mapPxH = mh * tileH.get();

        // grid
        g.setStroke(Color.color(1, 1, 1, 0.08));
        g.setLineWidth(1.0);
        for (int x = 0; x <= mw; x++) g.strokeLine(x * tileW.get(), 0, x * tileW.get(), mapPxH);
        for (int y = 0; y <= mh; y++) g.strokeLine(0, y * tileH.get(), mapPxW, y * tileH.get());

        // border
        g.setStroke(Color.color(1, 1, 1, 0.18));
        g.setLineWidth(2);
        g.strokeRect(0, 0, mapPxW, mapPxH);

        // background
        if (map != null && map.background != null && map.background.logicalPath != null && !map.background.logicalPath.isBlank()) {
            try {
                String abs = manager.toAbsolute(map.background.logicalPath);
                var img = new Image(Path.of(abs).toUri().toString());
                double scaleX = (map.widthTiles * tileW.get()) / Math.max(1.0, img.getWidth());
                double scaleY = (map.heightTiles * tileH.get()) / Math.max(1.0, img.getHeight());
                double scale = Math.min(scaleX, scaleY);
                g.drawImage(img, 0, 0, img.getWidth(), img.getHeight(), 0, 0, img.getWidth() * scale, img.getHeight() * scale);
            } catch (Exception ignored) {
            }
        }

        if (map != null) {
            List<MapDef.Placement> ordered = map.placements.stream()
                .sorted(Comparator.comparingInt((MapDef.Placement p) -> p.layer)
                    .thenComparingInt(map.placements::indexOf))
                .toList();
            for (MapDef.Placement p : ordered) drawPlacement(p);
        }

        if (preview != null) drawPreview(preview);

        g.restore();

        // HUD
        g.setFill(Color.color(1, 1, 1, 0.6));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        int count = map == null ? 0 : map.placements.size();
        g.fillText("placements: " + count + "   |   zoom: " + String.format("%.2f", zoom.get())
            + "   |   layer: " + currentLayer.get(), 6, 6);
    }

    /**
     * Does the map currently contain any animated placement (or an animated preview)?
     */
    private boolean hasAnimatedPlacements() {
        if (preview != null) return false; // preview isn't animated
        if (map == null || map.placements.isEmpty()) return false;
        for (MapDef.Placement p : map.placements) {
            TemplateDef d = p.dataSnap;
            if (d != null && d.complex && d.animated && d.regions != null && !d.regions.isEmpty())
                return true;
        }
        return false;
    }

    // --- First-frame helpers ---
    private boolean isAnimated(TemplateDef t) {
        return t != null && t.complex && t.animated && t.regions != null && !t.regions.isEmpty();
    }

    private int[] firstFrameRectPx(TemplateDef t) {
        // Returns [x,y,w,h] in pixels (falls back to whole image if no regions)
        if (isAnimated(t)) return t.pixelRegions().get(0);
        return new int[]{0, 0, Math.max(1, t.imageWidthPx), Math.max(1, t.imageHeightPx)};
    }

    private int[] firstFrameTilesWH(TemplateDef t) {
        int[] r = firstFrameRectPx(t);
        int wTiles = Math.max(1, (int)Math.round((double) r[2] / Math.max(1, t.tileWidthPx)));
        int hTiles = Math.max(1, (int)Math.round((double) r[3] / Math.max(1, t.tileHeightPx)));
        return new int[]{wTiles, hTiles};
    }

    /** Returns *base* tiles (unscaled) to use for bounds/hit-tests: first frame if animated, else p.wTiles/hTiles. */
    private int[] baseFootprintTiles(MapDef.Placement p) {
        TemplateDef snap = p.dataSnap;
        if (isAnimated(snap)) return firstFrameTilesWH(snap);
        return new int[]{Math.max(1, p.wTiles), Math.max(1, p.hTiles)};
    }

    /** For loaded maps created before this rule, normalize animated placements to first-frame footprint. */
    private void normalizeAnimatedFootprints() {
        if (map == null) return;
        for (MapDef.Placement p : map.placements) {
            if (isAnimated(p.dataSnap)) {
                int[] wh = firstFrameTilesWH(p.dataSnap);
                p.wTiles = wh[0];
                p.hTiles = wh[1];
            }
        }
    }

    /**
     * Map a running time to a frame index in [0..frames-1], with a 60-slot timeline.
     */
    private int animatedFrameIndex(int frames) {
        if (frames <= 0) return 0;
        long now = System.nanoTime();
        long slot = (now / (1_000_000_000L / 60)) % 60;
        if (frames >= 60) return (int) Math.floor(slot * (frames / 60.0));
        return (int) (slot % frames);
    }

    private void drawPlacement(MapDef.Placement p) {
        TemplateDef snap = p.dataSnap;
        if (snap == null) return;

        Image tex;
        try {
            String abs = manager.toAbsolute(snap.logicalPath);
            tex = new Image(Path.of(abs).toUri().toString());
        } catch (Exception e) {
            return;
        }

        // Fixed bounds from first frame (or base for non-animated), then scale
        int[] baseWH = baseFootprintTiles(p);
        int drawWtiles = Math.max(1, (int)Math.ceil(baseWH[0] * p.scale));
        int drawHtiles = Math.max(1, (int)Math.ceil(baseWH[1] * p.scale));

        double dx = p.gx * tileW.get();
        double dy = p.gy * tileH.get();
        double dw = drawWtiles * tileW.get();
        double dh = drawHtiles * tileH.get();

        // Choose source rect (current frame if animated; otherwise placement src)
        int sx = p.srcXpx, sy = p.srcYpx, sw = p.srcWpx, sh = p.srcHpx;
        if (isAnimated(snap)) {
            var frames = snap.pixelRegions();
            int idx = animatedFrameIndex(frames.size());
            int[] r = frames.get(Math.max(0, Math.min(idx, frames.size() - 1)));
            sx = r[0]; sy = r[1]; sw = r[2]; sh = r[3];
        }

        // Draw the texture to the fixed box (keeps animation inside first-frame footprint)
        g.drawImage(tex, sx, sy, sw, sh, dx, dy, dw, dh);

        // Selection/border
        if (p.pid.equals(selectedPid.get())) {
            g.setStroke(Color.color(0.95, 0.8, 0.2, 1));
            g.setLineWidth(3);
            g.strokeRect(dx + 0.5, dy + 0.5, dw - 1, dh - 1);
        } else {
            g.setStroke(Color.color(0, 0, 0, 0.6));
            g.setLineWidth(1);
            g.strokeRect(dx, dy, dw, dh);
        }

        // ----- Scaled overlays -----
        // We want to render gate/collision tiles *inside the fixed box*, proportionally scaled.
        // Compute how much a source "first-frame tile" maps to screen pixels.
        double tw = tileW.get(), th = tileH.get(); // editor’s tile draw size
        double scaleX = dw / (baseWH[0] * tw);
        double scaleY = dh / (baseWH[1] * th);
        double cellW  = tw * scaleX;
        double cellH  = th * scaleY;

        // Where to read tiles from:
        int tileOffsetX = 0, tileOffsetY = 0;
        if (isAnimated(snap)) {
            int[] off = firstFrameTileOffset(snap);
            tileOffsetX = off[0];
            tileOffsetY = off[1];
        }

        // Limit loops to the base (first-frame) footprint so overlays line up with the scaled box
        int cols = baseWH[0];
        int rows = baseWH[1];

        if (showGates.get()) {
            g.setFill(Color.color(0, 1, 0, 0.25));
            g.setStroke(Color.color(0, 1, 0, 0.9));
            g.setLineWidth(1.5);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = snap.tileAt(tileOffsetX + gx, tileOffsetY + gy);
                    if (t != null && t.gate) {
                        double x = dx + gx * cellW, y = dy + gy * cellH;
                        g.fillRect(x, y, cellW, cellH);
                        g.strokeRect(x + 0.5, y + 0.5, cellW - 1, cellH - 1);
                    }
                }
            }
        }

        if (showCollisions.get()) {
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2.0);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = snap.tileAt(tileOffsetX + gx, tileOffsetY + gy);
                    if (t == null || !t.solid) continue;
                    double x = dx + gx * cellW, y = dy + gy * cellH;
                    switch (t.shape) {
                        case RECT_FULL -> g.strokeRect(x, y, cellW, cellH);
                        case HALF_RECT ->
                            CollisionShapes.get(TemplateDef.ShapeType.HALF_RECT)
                                .draw(g, x, y, cellW, cellH, t.orientation);
                        case TRIANGLE  ->
                            CollisionShapes.get(TemplateDef.ShapeType.TRIANGLE)
                                .draw(g, x, y, cellW, cellH, t.orientation);
                        default -> { /* no-op */ }
                    }
                }
            }
        }
    }

    private void drawPreview(DropPreview pv) {
        double dx = pv.gx * tileW.get();
        double dy = pv.gy * tileH.get();
        double dw = pv.wTiles * tileW.get();
        double dh = pv.hTiles * tileH.get();
        if (pv.texture != null) {
            g.setGlobalAlpha(0.85);
            g.drawImage(pv.texture, pv.rpx, pv.rpy, pv.rpw, pv.rph, dx, dy, dw, dh);
            g.setGlobalAlpha(1.0);
        }
        g.setFill(Color.color(0, 0.8, 1, 0.20));
        g.fillRect(dx, dy, dw, dh);
        g.setStroke(Color.color(0, 0.8, 1, 0.9));
        g.setLineWidth(2);
        g.strokeRect(dx, dy, dw, dh);
    }

    private void deleteSelectedWithConfirm() {
        if (map == null) return;
        MapDef.Placement sel = getSelected();
        if (sel == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete selected placement?", ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        // remove links and metas pointing to this placement
        String pid = sel.pid;
        map.gateLinks.removeIf(gl ->
            (gl.a != null && pid.equals(gl.a.pid)) ||
                (gl.b != null && pid.equals(gl.b.pid))
        );
        map.gateMetas.removeIf(gm -> gm.ref != null && pid.equals(gm.ref.pid));

        map.placements.removeIf(p -> p.pid.equals(pid));
        selectPid(null);
        redraw();
    }

    public void bindMap(MapDef map) {
        this.map = map;
        if (map != null) {
            map.normalizeLayers();
            normalizeAnimatedFootprints(); // new: fix footprints for animated placements
        }
        layoutForMap();
    }

    /** Tile offset (in tiles) of first frame within the source texture. */
    private int[] firstFrameTileOffset(TemplateDef t) {
        int[] r = firstFrameRectPx(t);
        int offX = Math.max(0, r[0] / Math.max(1, t.tileWidthPx));
        int offY = Math.max(0, r[1] / Math.max(1, t.tileHeightPx));
        return new int[]{offX, offY};
    }

    private record DropPreview(
        String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles,
        int tW, int tH, int rpx, int rpy, int rpw, int rph, double scaleMul, Image texture
    ) {
    }
}
