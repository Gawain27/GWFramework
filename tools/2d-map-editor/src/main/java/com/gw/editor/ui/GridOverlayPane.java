package com.gw.editor.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public final class GridOverlayPane extends StackPane {

    private final ImageView imageView = new ImageView();
    private final Canvas grid = new Canvas();

    private final IntegerProperty tileW = new SimpleIntegerProperty(16);
    private final IntegerProperty tileH = new SimpleIntegerProperty(16);

    public GridOverlayPane() {
        getChildren().addAll(imageView, grid);

        // Fit image to pane
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.fitHeightProperty().bind(heightProperty());
        imageView.setSmooth(true);

        // Repaint triggers
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
        tileW.addListener((o, a, b) -> redraw());
        tileH.addListener((o, a, b) -> redraw());
        imageView.imageProperty().addListener((o, a, b) -> redraw());

        // Minimum size so SplitPane doesn't collapse it
        setMinSize(320, 240);
    }

    public ImageView getImageView() { return imageView; }
    public IntegerProperty tileWidthProperty() { return tileW; }
    public IntegerProperty tileHeightProperty() { return tileH; }

    private void redraw() {
        Bounds b = getLayoutBounds();
        double W = Math.max(0, b.getWidth());
        double H = Math.max(0, b.getHeight());

        grid.setWidth(W);
        grid.setHeight(H);

        GraphicsContext g = grid.getGraphicsContext2D();
        g.clearRect(0, 0, W, H);

        Image img = imageView.getImage();
        if (img == null) return;

        // Compute drawn image rect (preserve ratio: same math as ImageView)
        double imgW = img.getWidth(), imgH = img.getHeight();
        double scale = Math.min(W / imgW, H / imgH);
        double drawW = imgW * scale;
        double drawH = imgH * scale;
        double offX = (W - drawW) / 2.0;
        double offY = (H - drawH) / 2.0;

        int tw = Math.max(1, tileW.get());
        int th = Math.max(1, tileH.get());
        double stepX = tw * scale;
        double stepY = th * scale;

        // Slight outline behind grid for contrast
        g.setStroke(Color.color(0, 0, 0, 0.35));
        g.setLineWidth(2.0);
        // outer box
        g.strokeRect(offX, offY, drawW, drawH);

        // Inner grid (shadow pass)
        for (double x = offX; x <= offX + drawW + 0.5; x += stepX) g.strokeLine(x, offY, x, offY + drawH);
        for (double y = offY; y <= offY + drawH + 0.5; y += stepY) g.strokeLine(offX, y, offX + drawW, y);

        // Foreground lines (crisper, lighter)
        g.setStroke(Color.color(1, 1, 1, 0.75));
        g.setLineWidth(1.0);
        g.strokeRect(offX, offY, drawW, drawH);
        for (double x = offX; x <= offX + drawW + 0.5; x += stepX) g.strokeLine(x, offY, x, offY + drawH);
        for (double y = offY; y <= offY + drawH + 0.5; y += stepY) g.strokeLine(offX, y, offX + drawW, y);
    }
}
