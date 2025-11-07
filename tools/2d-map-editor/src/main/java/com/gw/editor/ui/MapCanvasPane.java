package com.gw.editor.ui;

import com.gw.editor.map.MapDef;
import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gwngames.core.api.asset.IAssetManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;

/**
 * Map canvas with grid, placements, live drop preview and zoom.
 * Accepts drags from the Template Gallery (custom DataFormat payload).
 */
public class MapCanvasPane extends Region {
    public static final DataFormat DND_FORMAT = new DataFormat("application/x-gw-template-drop");
    // Drag payload: templateId|regionIndex|tileW|tileH|regionPxX|regionPxY|regionPxW|regionPxH

    private final Canvas canvas = new Canvas();
    private final GraphicsContext g = canvas.getGraphicsContext2D();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

    private final TemplateRepository templateRepo;
    private final IAssetManager manager;

    private MapDef map; // current map (mutable model)
    private DropPreview preview = null;

    public MapCanvasPane(TemplateRepository repo, IAssetManager manager) {
        this.templateRepo = repo;
        this.manager = manager;

        getChildren().add(canvas);
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
        tileW.addListener((o, a, b) -> layoutForMap());
        tileH.addListener((o, a, b) -> layoutForMap());
        zoom.addListener((o, a, b) -> layoutForMap());

        setOnDragOver(this::onDragOver);
        setOnDragExited(e -> {
            preview = null;
            redraw();
        });
        setOnDragDropped(this::onDragDropped);

        // Zoom with CTRL + mouse wheel
        setOnScroll(e -> {
            if (!e.isControlDown() && !e.isShortcutDown()) return;
            if (e.getDeltaY() > 0) zoomIn();
            else zoomOut();
            e.consume();
        });

        setFocusTraversable(true);
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

    public void bindMap(MapDef map) {
        this.map = map;
        layoutForMap();
    }

    /**
     * Resize map (tiles). Returns false if shrink would cut off placements.
     */
    public boolean setMapSize(int widthTiles, int heightTiles) {
        if (map == null) return false;
        if (widthTiles < 1 || heightTiles < 1) return false;

        // prevent destructive shrink: any placement out of new bounds?
        if (widthTiles < map.widthTiles || heightTiles < map.heightTiles) {
            boolean anyOut = map.placements.stream().anyMatch(p ->
                p.gx < 0 || p.gy < 0 ||
                    p.gx + p.wTiles > widthTiles ||
                    p.gy + p.hTiles > heightTiles
            );
            if (anyOut) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                    "Cannot shrink map: one or more placements would be outside the bounds.");
                a.setHeaderText("Shrink refused");
                a.showAndWait();
                return false;
            }
        }

        map.widthTiles = widthTiles;
        map.heightTiles = heightTiles;
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

    public void addPlacement(MapDef.Placement p) {
        if (map == null) return;
        map.placements.add(p);
        redraw();
    }

    public void clearAll() {
        if (map != null) map.placements.clear();
        redraw();
    }

    private void onDragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (!db.hasContent(DND_FORMAT)) return;

        String payload = (String) db.getContent(DND_FORMAT);
        String[] tok = payload.split("\\|");
        if (tok.length < 8) return;

        String templateId = tok[0];
        int regionIndex = Integer.parseInt(tok[1]);
        int tW = Integer.parseInt(tok[2]);
        int tH = Integer.parseInt(tok[3]);
        int rpX = Integer.parseInt(tok[4]);
        int rpY = Integer.parseInt(tok[5]);
        int rpW = Integer.parseInt(tok[6]);
        int rpH = Integer.parseInt(tok[7]);

        // convert event position from screen pixels to "map pixels" (pre-zoom)
        double localX = e.getX() / zoom.get();
        double localY = e.getY() / zoom.get();

        // snap to tile
        int gx = (int) Math.floor(localX / tileW.get());
        int gy = (int) Math.floor(localY / tileH.get());

        // region size in tiles (for simple templates regionPx == whole image)
        int wTiles = Math.max(1, (int) Math.round((double) rpW / tW));
        int hTiles = Math.max(1, (int) Math.round((double) rpH / tH));

        // clamp footprint to map bounds if possible
        if (map != null) {
            gx = Math.max(0, Math.min(gx, Math.max(0, map.widthTiles - wTiles)));
            gy = Math.max(0, Math.min(gy, Math.max(0, map.heightTiles - hTiles)));
        }

        // fetch texture for preview
        Image texture = null;
        try {
            TemplateDef td = templateRepo.findById(templateId);
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
        if (preview == null) {
            e.setDropCompleted(false);
            return;
        }
        if (map == null) {
            e.setDropCompleted(false);
            return;
        }

        // final clamp
        int gx = Math.max(0, Math.min(preview.gx, map.widthTiles - preview.wTiles));
        int gy = Math.max(0, Math.min(preview.gy, map.heightTiles - preview.hTiles));

        MapDef.Placement p = new MapDef.Placement(
            preview.templateId, preview.regionIndex, gx, gy, preview.wTiles, preview.hTiles
        );
        addPlacement(p);
        preview = null;
        e.setDropCompleted(true);
        e.consume();
    }

    private void layoutForMap() {
        double pixelW = (map == null ? 64 : map.widthTiles) * tileW.get();
        double pixelH = (map == null ? 36 : map.heightTiles) * tileH.get();
        canvas.setWidth(Math.max(32, pixelW * zoom.get()));
        canvas.setHeight(Math.max(32, pixelH * zoom.get()));
        redraw();
    }

    private void redraw() {
        double W = canvas.getWidth();
        double H = canvas.getHeight();

        // background
        g.setFill(Color.color(0.07, 0.07, 0.07));
        g.fillRect(0, 0, W, H);

        g.save();
        g.scale(zoom.get(), zoom.get()); // draw everything in unzoomed units (tileW, tileH)

        // grid bounds (map size)
        int mw = map == null ? 64 : map.widthTiles;
        int mh = map == null ? 36 : map.heightTiles;

        double mapPxW = mw * tileW.get();
        double mapPxH = mh * tileH.get();

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

        // map boundary
        g.setStroke(Color.color(1, 1, 1, 0.18));
        g.setLineWidth(2);
        g.strokeRect(0, 0, mapPxW, mapPxH);

        // origin axes
        g.setStroke(Color.color(0.2, 0.8, 1, 0.6));
        g.setLineWidth(1.5);
        g.strokeLine(0, 0, mapPxW, 0);
        g.strokeLine(0, 0, 0, mapPxH);

        // placements
        if (map != null) {
            for (MapDef.Placement p : map.placements) drawPlacement(p);
        }

        // preview
        if (preview != null) drawPreview(preview);

        g.restore();

        // HUD (not zoomed)
        g.setFill(Color.color(1, 1, 1, 0.6));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        int count = map == null ? 0 : map.placements.size();
        g.fillText("placements: " + count + "   |   zoom: " + String.format("%.2f", zoom.get()), 6, 6);
    }

    private void drawPlacement(MapDef.Placement p) {
        TemplateDef td = templateRepo.findById(p.templateId);
        if (td == null) return;

        Image tex;
        try {
            String abs = manager.toAbsolute(td.logicalPath);
            tex = new Image(Path.of(abs).toUri().toString());
        } catch (Exception e) {
            return;
        }

        int sx = 0, sy = 0, sw = td.imageWidthPx, sh = td.imageHeightPx;
        if (td.complex && p.regionIndex >= 0 && p.regionIndex < td.pixelRegions().size()) {
            int[] r = td.pixelRegions().get(p.regionIndex);
            sx = r[0];
            sy = r[1];
            sw = r[2];
            sh = r[3];
        }

        double dx = p.gx * tileW.get();
        double dy = p.gy * tileH.get();
        double dw = p.wTiles * tileW.get();
        double dh = p.hTiles * tileH.get();

        g.drawImage(tex, sx, sy, sw, sh, dx, dy, dw, dh);

        // thin outline
        g.setStroke(Color.color(0, 0, 0, 0.6));
        g.setLineWidth(1);
        g.strokeRect(dx, dy, dw, dh);
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
