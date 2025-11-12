package com.gw.map.ui.plane;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.ui.CollisionShapes;
import com.gw.editor.util.TemplateSlice;
import com.gw.map.io.DefaultTextureResolver;
import com.gw.map.io.TextureResolver;
import com.gw.map.model.Plane2DMap;
import com.gw.map.ui.TemplateGalleryPane;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 2D plane editor canvas (ported from your MapCanvasPane and adapted).
 * - Supports drag/drop from TemplateGalleryPane (PAYLOAD_FORMAT)
 * - Selection, move, delete
 * - Zoom (Ctrl/Cmd + wheel)
 * - Animated placements (first-frame footprint)
 * - Gate/collision overlays
 * - Notifies PlaneSidebarPane via setOnSelectionChanged(...)
 */
public class PlaneCanvasPane extends Region {

    /**
     * Matches the gallery payload format.
     */
    public static final DataFormat PAYLOAD = TemplateGalleryPane.PAYLOAD_FORMAT;

    private final Canvas canvas = new Canvas();
    private final GraphicsContext g = canvas.getGraphicsContext2D();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

    private final BooleanProperty showCollisions = new SimpleBooleanProperty(true);
    private final BooleanProperty showGates = new SimpleBooleanProperty(true);
    private final IntegerProperty currentLayer = new SimpleIntegerProperty(0);

    private final TemplateRepository templateRepo;
    private final TextureResolver textureResolver = new DefaultTextureResolver();
    private final StringProperty selectedPid = new SimpleStringProperty(null);
    private Plane2DMap map;
    private DropPreview preview = null;
    private final AnimationTimer animTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (hasAnimatedPlacements()) redrawInternal();
        }
    };
    private boolean draggingInstance = false;
    private int dragOffsetGX = 0, dragOffsetGY = 0;
    private Consumer<Plane2DMap.Placement> onSelectionChanged;

    public PlaneCanvasPane(TemplateRepository repo) {
        this.templateRepo = (repo != null ? repo : new TemplateRepository());
        getChildren().add(canvas);

        // layout / paint
        widthProperty().addListener((o, a, b) -> redrawInternal());
        heightProperty().addListener((o, a, b) -> redrawInternal());
        tileW.addListener((o, a, b) -> layoutForMap());
        tileH.addListener((o, a, b) -> layoutForMap());
        zoom.addListener((o, a, b) -> layoutForMap());
        currentLayer.addListener((o, a, b) -> requestLayout());

        // DnD
        setOnDragOver(this::onDragOver);
        setOnDragExited(e -> {
            preview = null;
            redrawInternal();
        });
        setOnDragDropped(this::onDragDropped);

        // zoom
        setOnScroll(e -> {
            if (!e.isControlDown() && !e.isShortcutDown()) return;
            if (e.getDeltaY() > 0) zoomIn();
            else zoomOut();
            e.consume();
        });

        // selection / move / delete
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                deleteSelectedWithConfirm();
                e.consume();
            }
        });
        setFocusTraversable(true);

        animTimer.start();
    }

    /* =================== Public API (PlaneSidebarPane links against these) =================== */

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int parseInt(String s, int d) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return d;
        }
    }

    private static double parseDouble(String s, double d) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return d;
        }
    }

    public void bindMap(Plane2DMap map) {
        this.map = map;
        if (map != null) map.normalizeLayers();
        layoutForMap();
    }

    public void setOnSelectionChanged(Consumer<Plane2DMap.Placement> cb) {
        this.onSelectionChanged = cb;
    }

    public IntegerProperty tileWidthProperty() {
        return tileW;
    }

    public IntegerProperty tileHeightProperty() {
        return tileH;
    }

    public DoubleProperty zoomProperty() {
        return zoom;
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

    public void zoomIn() {
        zoom.set(Math.min(4.0, zoom.get() * 1.25));
    }

    public void zoomOut() {
        zoom.set(Math.max(0.25, zoom.get() / 1.25));
    }

    /* =================== Drag & Drop =================== */

    public void zoomReset() {
        zoom.set(1.0);
    }

    public void redraw() {
        redrawInternal();
    }

    /* =================== Selection & Moving =================== */

    public Plane2DMap.Placement getSelected() {
        if (map == null || selectedPid.get() == null) return null;
        return map.placements.stream().filter(p -> p.pid.equals(selectedPid.get())).findFirst().orElse(null);
    }

    private void onDragOver(DragEvent e) {
        var db = e.getDragboard();
        if (!db.hasContent(PAYLOAD)) return;

        String[] tok = ((String) db.getContent(PAYLOAD)).split("\\|");
        if (tok.length < 9) return;

        String templateId = tok[0];
        int regionIndex = parseInt(tok[1], -1);
        int tW = parseInt(tok[2], 16);
        int tH = parseInt(tok[3], 16);
        int rpX = parseInt(tok[4], 0);
        int rpY = parseInt(tok[5], 0);
        int rpW = parseInt(tok[6], 0);
        int rpH = parseInt(tok[7], 0);
        double scaleMul = parseDouble(tok[8], 1.0);

        double localX = e.getX() / zoom.get();
        double localY = e.getY() / zoom.get();
        int gx = (int) Math.floor(localX / tileW.get());
        int gy = (int) Math.floor(localY / tileH.get());

        int baseWTiles = Math.max(1, (int) Math.round(rpW / (double) Math.max(1, tW)));
        int baseHTiles = Math.max(1, (int) Math.round(rpH / (double) Math.max(1, tH)));
        int wTiles = Math.max(1, (int) Math.ceil(baseWTiles * Math.max(0.01, scaleMul)));
        int hTiles = Math.max(1, (int) Math.ceil(baseHTiles * Math.max(0.01, scaleMul)));

        if (map != null) {
            gx = clamp(gx, 0, Math.max(0, map.widthTiles - wTiles));
            gy = clamp(gy, 0, Math.max(0, map.heightTiles - hTiles));
        }

        Image texture = loadTemplateTexture(templateId);

        preview = new DropPreview(templateId, regionIndex, gx, gy, wTiles, hTiles, tW, tH, rpX, rpY, rpW, rpH, scaleMul, texture);

        e.acceptTransferModes(TransferMode.COPY);
        e.consume();
        redrawInternal();
    }

    private void onDragDropped(DragEvent e) {
        if (preview == null || map == null) {
            e.setDropCompleted(false);
            return;
        }

        TemplateDef src = templateRepo.findById(preview.templateId);
        TemplateDef snap = (preview.regionIndex >= 0 && src != null && src.regions != null && preview.rpw > 0 && preview.rph > 0) ? TemplateSlice.copyRegion(src, preview.rpx / Math.max(1, preview.tW), preview.rpy / Math.max(1, preview.tH), preview.rpx / Math.max(1, preview.tW) + Math.max(1, (int) Math.round(preview.rpw / (double) preview.tW)) - 1, preview.rpy / Math.max(1, preview.tH) + Math.max(1, (int) Math.round(preview.rph / (double) preview.tH)) - 1) : TemplateSlice.copyWhole(src);

        int[] baseWH = baseFootprintTiles(snap);
        double scl = preview.scaleMul <= 0 ? 1.0 : preview.scaleMul;
        int wTiles = Math.max(1, (int) Math.ceil(baseWH[0] * scl));
        int hTiles = Math.max(1, (int) Math.ceil(baseWH[1] * scl));

        int gx = clamp(preview.gx, 0, map.widthTiles - wTiles);
        int gy = clamp(preview.gy, 0, map.heightTiles - hTiles);
        int layerIdx = clamp(currentLayer.get(), 0, Math.max(0, map.layers.size() - 1));

        Plane2DMap.Placement p = new Plane2DMap.Placement(preview.templateId, preview.regionIndex, gx, gy, baseWH[0], baseWH[1], preview.rpx, preview.rpy, preview.rpw, preview.rph, snap, layerIdx, scl);

        map.placements.add(p);
        preview = null;
        selectPid(p.pid);
        e.setDropCompleted(true);
        e.consume();
    }

    private void onMousePressed(MouseEvent e) {
        if (map == null) return;
        int gx = (int) Math.floor((e.getX() / zoom.get()) / tileW.get());
        int gy = (int) Math.floor((e.getY() / zoom.get()) / tileH.get());
        Plane2DMap.Placement hit = hitTestTopMost(gx, gy);
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

    /* =================== Rendering =================== */

    private void onMouseDragged(MouseEvent e) {
        if (!draggingInstance || map == null) return;
        Plane2DMap.Placement sel = getSelected();
        if (sel == null) return;

        int gx = (int) Math.floor((e.getX() / zoom.get()) / tileW.get()) - dragOffsetGX;
        int gy = (int) Math.floor((e.getY() / zoom.get()) / tileH.get()) - dragOffsetGY;

        int[] wh = baseFootprintTiles(sel.dataSnap);
        int wTiles = Math.max(1, (int) Math.ceil(wh[0] * sel.scale));
        int hTiles = Math.max(1, (int) Math.ceil(wh[1] * sel.scale));

        sel.gx = clamp(gx, 0, map.widthTiles - wTiles);
        sel.gy = clamp(gy, 0, map.heightTiles - hTiles);
        redrawInternal();
        e.consume();
    }

    private void onMouseReleased(MouseEvent e) {
        draggingInstance = false;
        e.consume();
    }

    private Plane2DMap.Placement hitTestTopMost(int gx, int gy) {
        if (map == null) return null;
        var ordered = map.placements.stream().sorted(Comparator.comparingInt((Plane2DMap.Placement p) -> p.layer).thenComparingInt(map.placements::indexOf)).toList();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Plane2DMap.Placement p = ordered.get(i);
            int[] wh = baseFootprintTiles(p.dataSnap);
            int wTiles = Math.max(1, (int) Math.ceil(wh[0] * p.scale));
            int hTiles = Math.max(1, (int) Math.ceil(wh[1] * p.scale));
            if (gx >= p.gx && gx < p.gx + wTiles && gy >= p.gy && gy < p.gy + hTiles) return p;
        }
        return null;
    }

    private void layoutForMap() {
        double pixelW = (map == null ? 64 : map.widthTiles) * tileW.get();
        double pixelH = (map == null ? 36 : map.heightTiles) * tileH.get();
        canvas.setWidth(Math.max(32, pixelW * zoom.get()));
        canvas.setHeight(Math.max(32, pixelH * zoom.get()));
        redrawInternal();
    }

    private void redrawInternal() {
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

        // placements (by layer)
        if (map != null) {
            var ordered = map.placements.stream().sorted(Comparator.comparingInt((Plane2DMap.Placement p) -> p.layer).thenComparingInt(map.placements::indexOf)).toList();
            for (Plane2DMap.Placement p : ordered) drawPlacement(p);
        }

        // live preview
        if (preview != null) drawPreview(preview);

        g.restore();

        // HUD
        g.setFill(Color.color(1, 1, 1, 0.6));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        int count = map == null ? 0 : map.placements.size();
        g.fillText("placements: " + count + "   |   zoom: " + String.format("%.2f", zoom.get()) + "   |   layer: " + currentLayer.get(), 6, 6);
    }

    private boolean hasAnimatedPlacements() {
        if (preview != null) return false;
        if (map == null || map.placements.isEmpty()) return false;
        for (Plane2DMap.Placement p : map.placements) {
            TemplateDef d = p.dataSnap;
            if (d != null && d.complex && d.animated && d.regions != null && !d.regions.isEmpty()) return true;
        }
        return false;
    }

    /* =================== Helpers =================== */

    private void drawPlacement(Plane2DMap.Placement p) {
        TemplateDef snap = p.dataSnap;
        if (snap == null) return;

        Image tex = loadTextureByLogicalPath(snap.logicalPath);
        if (tex == null) { // fallback from template id
            TemplateDef td = templateRepo.findById(p.templateId);
            if (td != null) tex = loadTextureByLogicalPath(td.logicalPath);
        }
        if (tex == null) return;

        int[] baseWH = baseFootprintTiles(snap);
        int drawWtiles = Math.max(1, (int) Math.ceil(baseWH[0] * p.scale));
        int drawHtiles = Math.max(1, (int) Math.ceil(baseWH[1] * p.scale));

        double dx = p.gx * tileW.get();
        double dy = p.gy * tileH.get();
        double dw = drawWtiles * tileW.get();
        double dh = drawHtiles * tileH.get();

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

        // draw
        g.drawImage(tex, sx, sy, sw, sh, dx, dy, dw, dh);

        // selection rect
        if (p.pid.equals(selectedPid.get())) {
            g.setStroke(Color.color(0.95, 0.8, 0.2, 1));
            g.setLineWidth(3);
            g.strokeRect(dx + 0.5, dy + 0.5, dw - 1, dh - 1);
        } else {
            g.setStroke(Color.color(0, 0, 0, 0.6));
            g.setLineWidth(1);
            g.strokeRect(dx, dy, dw, dh);
        }

        // overlays
        double tw = tileW.get(), th = tileH.get();
        double scaleX = dw / (baseWH[0] * tw);
        double scaleY = dh / (baseWH[1] * th);
        double cellW = tw * scaleX;
        double cellH = th * scaleY;

        int tileOffsetX = 0, tileOffsetY = 0;
        if (isAnimated(snap)) {
            int[] r = firstFrameRectPx(snap);
            tileOffsetX = Math.max(0, r[0] / Math.max(1, snap.tileWidthPx));
            tileOffsetY = Math.max(0, r[1] / Math.max(1, snap.tileHeightPx));
        }

        int cols = baseWH[0], rows = baseWH[1];

        if (showGates.get()) {
            g.setFill(Color.color(0, 1, 0, 0.25));
            g.setStroke(Color.color(0, 1, 0, 0.9));
            g.setLineWidth(1.5);
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < cols; x++) {
                    TemplateDef.TileDef t = snap.tileAt(tileOffsetX + x, tileOffsetY + y);
                    if (t != null && t.gate) {
                        double rx = dx + x * cellW, ry = dy + y * cellH;
                        g.fillRect(rx, ry, cellW, cellH);
                        g.strokeRect(rx + 0.5, ry + 0.5, cellW - 1, cellH - 1);
                    }
                }
        }

        if (showCollisions.get()) {
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2.0);
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < cols; x++) {
                    TemplateDef.TileDef t = snap.tileAt(tileOffsetX + x, tileOffsetY + y);
                    if (t == null || !t.solid) continue;
                    double rx = dx + x * cellW, ry = dy + y * cellH;
                    switch (t.shape) {
                        case RECT_FULL -> g.strokeRect(rx, ry, cellW, cellH);
                        case HALF_RECT ->
                            CollisionShapes.get(TemplateDef.ShapeType.HALF_RECT).draw(g, rx, ry, cellW, cellH, t.orientation);
                        case TRIANGLE ->
                            CollisionShapes.get(TemplateDef.ShapeType.TRIANGLE).draw(g, rx, ry, cellW, cellH, t.orientation);
                        default -> {
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
        Plane2DMap.Placement sel = getSelected();
        if (sel == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete selected placement?", ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        String pid = sel.pid;
        map.gateLinks.removeIf(gl -> (gl.a != null && pid.equals(gl.a.pid())) || (gl.b != null && pid.equals(gl.b.pid())));
        map.gateMetas.removeIf(gm -> gm.ref != null && pid.equals(gm.ref.pid()));

        map.placements.removeIf(p -> p.pid.equals(pid));
        selectPid(null);
        redrawInternal();
    }

    private void selectPid(String pid) {
        selectedPid.set(pid);
        if (onSelectionChanged != null) onSelectionChanged.accept(getSelected());
        redrawInternal();
        requestFocus();
    }

    private int animatedFrameIndex(int frames) {
        if (frames <= 0) return 0;
        long now = System.nanoTime();
        long slot = (now / (1_000_000_000L / 60)) % 60;
        if (frames >= 60) return (int) Math.floor(slot * (frames / 60.0));
        return (int) (slot % frames);
    }

    private boolean isAnimated(TemplateDef t) {
        return t != null && t.complex && t.animated && t.regions != null && !t.regions.isEmpty();
    }

    private int[] firstFrameRectPx(TemplateDef t) {
        if (isAnimated(t)) return t.pixelRegions().get(0);
        return new int[]{0, 0, Math.max(1, t.imageWidthPx), Math.max(1, t.imageHeightPx)};
    }

    private int[] baseFootprintTiles(TemplateDef snap) {
        if (isAnimated(snap)) {
            int[] r = snap.pixelRegions().get(0);
            int w = Math.max(1, (int) Math.round(r[2] / (double) Math.max(1, snap.tileWidthPx)));
            int h = Math.max(1, (int) Math.round(r[3] / (double) Math.max(1, snap.tileHeightPx)));
            return new int[]{w, h};
        }
        return new int[]{Math.max(1, snap.imageWidthPx / Math.max(1, snap.tileWidthPx)), Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx))};
    }

    private Image loadTemplateTexture(String templateId) {
        TemplateDef td = templateRepo.findById(templateId);
        if (td == null || td.logicalPath == null) return null;
        return loadTextureByLogicalPath(td.logicalPath);
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

    /**
     * Preview container.
     */
    private record DropPreview(String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles, int tW,
                               int tH, int rpx, int rpy, int rpw, int rph, double scaleMul, Image texture) {
    }
}
