package com.gw.editor.ui;

import com.gw.editor.map.MapDef;
import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateSlice;
import com.gwngames.core.api.asset.IAssetManager;
import javafx.beans.property.*;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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

    /**
     * Visibility toggles.
     */
    private final BooleanProperty showCollisions = new SimpleBooleanProperty(true);
    private final BooleanProperty showGates = new SimpleBooleanProperty(true);

    /**
     * Currently selected layer index for new drops.
     */
    private final IntegerProperty currentLayer = new SimpleIntegerProperty(0);

    private final TemplateRepository templateRepo;
    private final IAssetManager manager;

    private MapDef map;
    private DropPreview preview = null;

    // selection & move
    private final StringProperty selectedPid = new SimpleStringProperty(null);
    private boolean draggingInstance = false;
    private int dragOffsetGX = 0, dragOffsetGY = 0;
    private java.util.function.Consumer<MapDef.Placement> onSelectionChanged;

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

        setFocusTraversable(true);
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

    public void bindMap(MapDef map) {
        this.map = map;
        if (map != null) map.normalizeLayers();
        layoutForMap();
    }

    public boolean setMapSize(int w, int h) {
        if (map == null) return false;
        if (w < 1 || h < 1) return false;
        boolean anyOut = map.placements.stream().anyMatch(p ->
            p.gx < 0 || p.gy < 0 || p.gx + p.wTiles > w || p.gy + p.hTiles > h
        );
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
    }

    /* ---------- DnD ---------- */
    private void onDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (!db.hasContent(DND_FORMAT)) return;

        String[] tok = ((String) db.getContent(DND_FORMAT)).split("\\|");
        if (tok.length < 8) return;

        String templateId = tok[0];
        int regionIndex = Integer.parseInt(tok[1]);
        int tW = Integer.parseInt(tok[2]);
        int tH = Integer.parseInt(tok[3]);
        int rpX = Integer.parseInt(tok[4]);
        int rpY = Integer.parseInt(tok[5]);
        int rpW = Integer.parseInt(tok[6]);
        int rpH = Integer.parseInt(tok[7]);

        double localX = e.getX() / zoom.get();
        double localY = e.getY() / zoom.get();
        int gx = (int) Math.floor(localX / tileW.get());
        int gy = (int) Math.floor(localY / tileH.get());
        int wTiles = Math.max(1, (int) Math.round((double) rpW / tW));
        int hTiles = Math.max(1, (int) Math.round((double) rpH / tH));

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

        preview = new DropPreview(templateId, regionIndex, gx, gy, wTiles, hTiles, tW, tH, rpX, rpY, rpW, rpH, texture);
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
        TemplateDef snap;
        if (preview.regionIndex >= 0 && src != null && src.regions != null && preview.rpw > 0 && preview.rph > 0) {
            int x0 = preview.rpx / Math.max(1, preview.tW);
            int y0 = preview.rpy / Math.max(1, preview.tH);
            int wT = Math.max(1, (int) Math.round((double) preview.rpw / preview.tW));
            int hT = Math.max(1, (int) Math.round((double) preview.rph / preview.tH));
            snap = TemplateSlice.copyRegion(src, x0, y0, x0 + wT - 1, y0 + hT - 1);
        } else {
            snap = TemplateSlice.copyWhole(src);
        }

        int wTiles = Math.max(1, snap.imageWidthPx / Math.max(1, snap.tileWidthPx));
        int hTiles = Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx));
        int gx = Math.max(0, Math.min(preview.gx, map.widthTiles - wTiles));
        int gy = Math.max(0, Math.min(preview.gy, map.heightTiles - hTiles));

        int layerIdx = Math.max(0, Math.min(currentLayer.get(), Math.max(0, map.layers.size() - 1)));

        MapDef.Placement p = new MapDef.Placement(
            preview.templateId, preview.regionIndex, gx, gy,
            wTiles, hTiles,
            preview.rpx, preview.rpy, preview.rpw, preview.rph,
            snap, layerIdx
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

        gx = Math.max(0, Math.min(gx, map.widthTiles - sel.wTiles));
        gy = Math.max(0, Math.min(gy, map.heightTiles - sel.hTiles));

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
        // Top-most = highest layer, last in draw order
        List<MapDef.Placement> ordered = map.placements.stream()
            .sorted(Comparator.comparingInt((MapDef.Placement p) -> p.layer)
                .thenComparingInt(map.placements::indexOf))
            .toList();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MapDef.Placement p = ordered.get(i);
            if (gx >= p.gx && gx < p.gx + p.wTiles && gy >= p.gy && gy < p.gy + p.hTiles) return p;
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
        for (int x = 0; x <= mw; x++) {
            double px = x * tileW.get();
            g.strokeLine(px, 0, px, mapPxH);
        }
        for (int y = 0; y <= mh; y++) {
            double py = y * tileH.get();
            g.strokeLine(0, py, mapPxW, py);
        }

        // border
        g.setStroke(Color.color(1, 1, 1, 0.18));
        g.setLineWidth(2);
        g.strokeRect(0, 0, mapPxW, mapPxH);

        if (map != null) {
            // draw by layer order (ascending)
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

        double dx = p.gx * tileW.get();
        double dy = p.gy * tileH.get();
        double dw = p.wTiles * tileW.get();
        double dh = p.hTiles * tileH.get();
        g.drawImage(tex, p.srcXpx, p.srcYpx, p.srcWpx, p.srcHpx, dx, dy, dw, dh);

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
        int cols = Math.max(1, snap.imageWidthPx / Math.max(1, snap.tileWidthPx));
        int rows = Math.max(1, snap.imageHeightPx / Math.max(1, snap.tileHeightPx));
        double tw = tileW.get(), th = tileH.get();

        if (showGates.get()) {
            g.setFill(Color.color(0, 1, 0, 0.25));
            g.setStroke(Color.color(0, 1, 0, 0.9));
            g.setLineWidth(1.5);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = snap.tileAt(gx, gy);
                    if (t != null && t.gate) {
                        double x = dx + gx * tw, y = dy + gy * th;
                        g.fillRect(x, y, tw, th);
                        g.strokeRect(x + 0.5, y + 0.5, tw - 1, th - 1);
                    }
                }
            }
        }

        if (showCollisions.get()) {
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2.0);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = snap.tileAt(gx, gy);
                    if (t == null || !t.solid) continue;
                    double x = dx + gx * tw, y = dy + gy * th;
                    switch (t.shape) {
                        case RECT_FULL -> g.strokeRect(x, y, tw, th);
                        case HALF_RECT -> drawHalfRect(x, y, tw, th, t.orientation);
                        case TRIANGLE -> drawTriangle(x, y, tw, th, t.orientation);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    private void drawHalfRect(double x, double y, double tw, double th, TemplateDef.Orientation o) {
        CollisionShapes.CollisionShapeStrategy rect = CollisionShapes.get(TemplateDef.ShapeType.HALF_RECT);
        rect.draw(g, x, y, tw, th, o);
    }

    private void drawTriangle(double x, double y, double tw, double th, TemplateDef.Orientation o) {
        CollisionShapes.CollisionShapeStrategy trg = CollisionShapes.get(TemplateDef.ShapeType.TRIANGLE);
        trg.draw(g, x, y, tw, th, o);
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

    private record DropPreview(
        String templateId, int regionIndex, int gx, int gy, int wTiles, int hTiles,
        int tW, int tH, int rpx, int rpy, int rpw, int rph, Image texture
    ) {
    }
}
