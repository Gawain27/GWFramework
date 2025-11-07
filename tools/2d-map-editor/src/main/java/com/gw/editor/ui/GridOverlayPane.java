package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateDef.Orientation;
import com.gw.editor.template.TemplateDef.ShapeType;
import javafx.beans.property.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.awt.Point;
import java.util.*;

public final class GridOverlayPane extends StackPane {

    public enum CropMode { UP, DOWN }

    @FunctionalInterface
    public interface CollisionProvider { TemplateDef.TileDef tileAt(int gx, int gy); }

    @FunctionalInterface
    public interface SelectionListener { void onSelectionChanged(Set<Point> selection); }

    @FunctionalInterface
    public interface RegionsProvider { List<TemplateDef.RegionDef> regions(); }

    private CollisionProvider collisionProvider;
    private SelectionListener selectionListener;
    private RegionsProvider regionsProvider;

    private final ImageView imageView = new ImageView();
    private final Canvas grid = new Canvas();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);
    private final ObjectProperty<CropMode> cropMode = new SimpleObjectProperty<>(CropMode.DOWN);

    private int cols = 0, rows = 0;
    private double drawOffX = 0, drawOffY = 0, drawW = 0, drawH = 0, scale = 1.0;

    // Multi-select
    private final LinkedHashSet<Point> selection = new LinkedHashSet<>();
    private Point anchor = null;

    public GridOverlayPane() {
        getChildren().addAll(imageView, grid);

        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());
        imageView.setSmooth(true);

        widthProperty().addListener((o,a,b) -> redraw());
        heightProperty().addListener((o,a,b) -> redraw());
        tileW.addListener((o,a,b) -> redraw());
        tileH.addListener((o,a,b) -> redraw());
        imageView.imageProperty().addListener((o,a,b) -> redraw());
        cropMode.addListener((o,a,b) -> redraw());

        addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleClick);

        setMinSize(320, 240);
        setFocusTraversable(true); // keyboard events
    }

    public ImageView getImageView() { return imageView; }
    public IntegerProperty tileWidthProperty()  { return tileW; }
    public IntegerProperty tileHeightProperty() { return tileH; }
    public ObjectProperty<CropMode> cropModeProperty() { return cropMode; }

    public void setCollisionProvider(CollisionProvider provider) { this.collisionProvider = provider; redraw(); }
    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }
    public void setRegionsProvider(RegionsProvider p) { this.regionsProvider = p; }

    /** Public refresh hook for external UI to repaint overlays immediately. */
    public void refresh() { redraw(); }

    public Set<Point> getSelection() { return Collections.unmodifiableSet(selection); }
    public void clearSelection() { selection.clear(); anchor = null; fireSel(); redraw(); }
    public Point getPrimarySelection() { return selection.isEmpty() ? null : (anchor != null ? new Point(anchor) : selection.iterator().next()); }

    private void handleClick(MouseEvent ev) {
        if (ev.getButton() != MouseButton.PRIMARY) return;
        requestFocus();
        Point p = pixelToTile((int)ev.getX(), (int)ev.getY());
        if (p == null) return;

        boolean shift = ev.isShiftDown();
        boolean ctrl  = ev.isControlDown() || ev.isMetaDown();

        if (!shift && !ctrl) {
            selection.clear();
            selection.add(p);
            anchor = new Point(p);
        } else if (shift) {
            if (anchor == null) anchor = new Point(p);
            selection.clear();
            int minX = Math.min(anchor.x, p.x), maxX = Math.max(anchor.x, p.x);
            int minY = Math.min(anchor.y, p.y), maxY = Math.max(anchor.y, p.y);
            for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) selection.add(new Point(x, y));
        } else {
            if (selection.contains(p)) selection.remove(p); else selection.add(p);
            if (anchor == null) anchor = new Point(p);
        }

        fireSel();
        redraw();
    }

    private void fireSel() {
        if (selectionListener != null) selectionListener.onSelectionChanged(getSelection());
    }

    private void computeGrid() {
        Image img = imageView.getImage();
        cols = rows = 0;
        drawOffX = drawOffY = drawW = drawH = scale = 0.0;
        if (img == null) return;

        double imgW = img.getWidth(), imgH = img.getHeight();
        int tW = Math.max(1, tileW.get());
        int tH = Math.max(1, tileH.get());

        double idealCols = imgW / tW;
        double idealRows = imgH / tH;

        cols = cropMode.get() == CropMode.UP ? (int)Math.ceil(idealCols) : (int)Math.floor(idealCols);
        rows = cropMode.get() == CropMode.UP ? (int)Math.ceil(idealRows) : (int)Math.floor(idealRows);
        if (cols <= 0) cols = 1;
        if (rows <= 0) rows = 1;

        double effectivePxW = cols * tW;
        double effectivePxH = rows * tH;

        double viewW = getWidth()  <= 0 ? getPrefWidth()  : getWidth();
        double viewH = getHeight() <= 0 ? getPrefHeight() : getHeight();

        scale = Math.min(viewW / effectivePxW, viewH / effectivePxH);
        drawW = effectivePxW * scale;
        drawH = effectivePxH * scale;
        drawOffX = (viewW - drawW) / 2.0;
        drawOffY = (viewH - drawH) / 2.0;
    }

    private void redraw() {
        computeGrid();

        GraphicsContext g = grid.getGraphicsContext2D();
        double W = Math.max(0, getWidth());
        double H = Math.max(0, getHeight());
        grid.setWidth(W);
        grid.setHeight(H);
        g.clearRect(0, 0, W, H);

        Image img = imageView.getImage();
        if (img == null) return;

        double srcW = Math.min(cols * tileW.get(), img.getWidth());
        double srcH = Math.min(rows * tileH.get(), img.getHeight());

        g.drawImage(img, 0, 0, srcW, srcH, drawOffX, drawOffY, drawW, drawH);

        double stepX = tileW.get() * scale, stepY = tileH.get() * scale;

        // border + grid
        g.setStroke(Color.color(0,0,0,0.45)); g.setLineWidth(2);
        g.strokeRect(drawOffX, drawOffY, drawW, drawH);

        g.setStroke(Color.color(0,0,0,0.25)); g.setLineWidth(2);
        for (int c = 1; c < cols; c++) g.strokeLine(drawOffX + c*stepX, drawOffY, drawOffX + c*stepX, drawOffY + drawH);
        for (int r = 1; r < rows; r++) g.strokeLine(drawOffX, drawOffY + r*stepY, drawOffX + drawW, drawOffY + r*stepY);

        g.setStroke(Color.color(1,1,1,0.9)); g.setLineWidth(1);
        for (int c = 1; c < cols; c++) g.strokeLine(drawOffX + c*stepX, drawOffY, drawOffX + c*stepX, drawOffY + drawH);
        for (int r = 1; r < rows; r++) g.strokeLine(drawOffX, drawOffY + r*stepY, drawOffX + drawW, drawOffY + r*stepY);

        // GATE overlay (green, drawn first so collision shows above if needed)
        if (collisionProvider != null) {
            g.setFill(Color.color(0, 1, 0, 0.22));
            g.setStroke(Color.color(0, 0.7, 0, 0.9));
            g.setLineWidth(2);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = collisionProvider.tileAt(gx, gy);
                    if (t == null || !t.gate) continue;
                    double x = drawOffX + gx*stepX, y = drawOffY + gy*stepY;
                    g.fillRect(x, y, stepX, stepY);
                    g.strokeRect(x, y, stepX, stepY);
                }
            }
        }

        // Collision overlays (yellow)
        if (collisionProvider != null) {
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    TemplateDef.TileDef t = collisionProvider.tileAt(gx, gy);
                    if (t == null || !t.solid) continue;

                    double x = drawOffX + gx * stepX;
                    double y = drawOffY + gy * stepY;
                    var shape = t.shape == null ? ShapeType.RECT_FULL : t.shape;
                    var o = t.orientation == null ? Orientation.UP : t.orientation;
                    CollisionShapes.get(shape).draw(g, x, y, stepX, stepY, o);
                }
            }
        }

        // Regions overlay (red border)
        if (regionsProvider != null) {
            var regions = regionsProvider.regions();
            if (regions != null) {
                g.setStroke(Color.color(1, 0, 0, 0.95));
                g.setLineWidth(3);
                for (var r : regions) {
                    double x = drawOffX + r.x0 * stepX;
                    double y = drawOffY + r.y0 * stepY;
                    double w = (r.x1 - r.x0 + 1) * stepX;
                    double h = (r.y1 - r.y0 + 1) * stepY;
                    g.strokeRect(x, y, w, h);
                }
            }
        }

        // selection overlay (blue)
        g.setStroke(Color.color(0, 0.5, 1, 0.9));
        g.setLineWidth(2);
        g.setFill(Color.color(0, 0.5, 1, 0.22));
        for (Point sel : selection) {
            double x = drawOffX + sel.x * stepX, y = drawOffY + sel.y * stepY;
            g.fillRect(x, y, stepX, stepY);
            g.strokeRect(x, y, stepX, stepY);
        }
    }

    private Point pixelToTile(int mx, int my) {
        if (cols == 0 || rows == 0) return null;
        if (mx < drawOffX || mx > drawOffX + drawW || my < drawOffY || my > drawOffY + drawH) return null;
        double relX = mx - drawOffX, relY = my - drawOffY;
        int gx = (int)Math.floor(relX / (tileW.get() * scale));
        int gy = (int)Math.floor(relY / (tileH.get() * scale));
        if (gx < 0 || gx >= cols || gy < 0 || gy >= rows) return null;
        return new Point(gx, gy);
    }
}
