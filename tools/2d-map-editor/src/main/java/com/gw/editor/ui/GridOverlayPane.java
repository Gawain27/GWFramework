package com.gw.editor.ui;

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
import java.util.function.BiConsumer;

/**
 * GridOverlayPane
 * - Enforces integer tile grid (cols, rows) by rounding up or down.
 * - Optionally crops drawing area so the grid is perfectly divisible.
 * - Emits selected tile via selectedTileProperty().
 */
public final class GridOverlayPane extends StackPane {

    public enum CropMode {UP, DOWN} // round to ceil/floor tile counts

    private final ImageView imageView = new ImageView();
    private final Canvas grid = new Canvas();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);
    private final ObjectProperty<CropMode> cropMode = new SimpleObjectProperty<>(CropMode.DOWN);

    private int cols = 0, rows = 0;
    private double drawOffX = 0, drawOffY = 0, drawW = 0, drawH = 0, scale = 1.0;

    private final ObjectProperty<Point> selectedTile = new SimpleObjectProperty<>(null);
    private BiConsumer<Integer, Integer> tileClickHandler;

    public GridOverlayPane() {
        getChildren().addAll(imageView, grid);

        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());
        imageView.setSmooth(true);

        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
        tileW.addListener((o, a, b) -> redraw());
        tileH.addListener((o, a, b) -> redraw());
        imageView.imageProperty().addListener((o, a, b) -> redraw());
        cropMode.addListener((o, a, b) -> redraw());
        selectedTile.addListener((o, a, b) -> redraw()); // repaint on selection

        addEventHandler(MouseEvent.MOUSE_CLICKED, ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) return;
            Point p = pixelToTile((int) ev.getX(), (int) ev.getY());
            selectedTile.set(p);
            if (p != null && tileClickHandler != null) tileClickHandler.accept(p.x, p.y);
        });

        setMinSize(320, 240);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public IntegerProperty tileWidthProperty() {
        return tileW;
    }

    public IntegerProperty tileHeightProperty() {
        return tileH;
    }

    public ObjectProperty<CropMode> cropModeProperty() {
        return cropMode;
    }

    public ReadOnlyObjectProperty<Point> selectedTileProperty() {
        return selectedTile;
    }

    public void setTileClickHandler(BiConsumer<Integer, Integer> h) {
        this.tileClickHandler = h;
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

        cols = cropMode.get() == CropMode.UP ? (int) Math.ceil(idealCols) : (int) Math.floor(idealCols);
        rows = cropMode.get() == CropMode.UP ? (int) Math.ceil(idealRows) : (int) Math.floor(idealRows);
        if (cols <= 0) cols = 1;
        if (rows <= 0) rows = 1;

        double effectivePxW = cols * tW;
        double effectivePxH = rows * tH;

        double viewW = getWidth() <= 0 ? getPrefWidth() : getWidth();
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

        // border + grid (shadow then light)
        g.setStroke(Color.color(0, 0, 0, 0.45));
        g.setLineWidth(2);
        g.strokeRect(drawOffX, drawOffY, drawW, drawH);

        g.setStroke(Color.color(0, 0, 0, 0.25));
        g.setLineWidth(2);
        for (int c = 1; c < cols; c++)
            g.strokeLine(drawOffX + c * stepX, drawOffY, drawOffX + c * stepX, drawOffY + drawH);
        for (int r = 1; r < rows; r++)
            g.strokeLine(drawOffX, drawOffY + r * stepY, drawOffX + drawW, drawOffY + r * stepY);

        g.setStroke(Color.color(1, 1, 1, 0.9));
        g.setLineWidth(1);
        for (int c = 1; c < cols; c++)
            g.strokeLine(drawOffX + c * stepX, drawOffY, drawOffX + c * stepX, drawOffY + drawH);
        for (int r = 1; r < rows; r++)
            g.strokeLine(drawOffX, drawOffY + r * stepY, drawOffX + drawW, drawOffY + r * stepY);

        var sel = selectedTile.get();
        if (sel != null && sel.x >= 0 && sel.x < cols && sel.y >= 0 && sel.y < rows) {
            double x = drawOffX + sel.x * stepX, y = drawOffY + sel.y * stepY;
            g.setFill(Color.color(0, 0.5, 1, 0.25));
            g.fillRect(x, y, stepX, stepY);
            g.setStroke(Color.color(0, 0.5, 1, 0.9));
            g.setLineWidth(2);
            g.strokeRect(x, y, stepX, stepY);
        }
    }

    private Point pixelToTile(int mx, int my) {
        if (cols == 0 || rows == 0) return null;
        if (mx < drawOffX || mx > drawOffX + drawW || my < drawOffY || my > drawOffY + drawH) return null;
        double relX = mx - drawOffX, relY = my - drawOffY;
        int gx = (int) Math.floor(relX / (tileW.get() * scale));
        int gy = (int) Math.floor(relY / (tileH.get() * scale));
        if (gx < 0 || gx >= cols || gy < 0 || gy >= rows) return null;
        return new Point(gx, gy);
    }

    // Optional getters for external logic
    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public double getScale() {
        return scale;
    }

    public double getDrawOffsetX() {
        return drawOffX;
    }

    public double getDrawOffsetY() {
        return drawOffY;
    }
}

