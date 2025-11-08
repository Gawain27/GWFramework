package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef.Orientation;
import com.gw.editor.template.TemplateDef.ShapeType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Map;

public final class CollisionShapes {
    private CollisionShapes() {
    }

    public interface CollisionShapeStrategy {
        void draw(GraphicsContext g, double x, double y, double w, double h, Orientation o);
    }

    private static final Map<ShapeType, CollisionShapeStrategy> STRATS = new EnumMap<>(ShapeType.class);

    static {
        // Full rectangle
        STRATS.put(ShapeType.RECT_FULL, (g, x, y, w, h, o) -> {
            g.setFill(Color.color(1, 1, 0, 0.25));
            g.fillRect(x, y, w, h);
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2);
            g.strokeRect(x, y, w, h);
        });

        // Half rectangle (orientation chooses which half)
        STRATS.put(ShapeType.HALF_RECT, (g, x, y, w, h, o) -> {
            double hx = x, hy = y, hw = w, hh = h;
            switch (o) {
                case UP -> {
                    hy = y;
                    hh = h / 2;
                }
                case DOWN -> {
                    hy = y + h / 2;
                    hh = h / 2;
                }
                case LEFT -> {
                    hx = x;
                    hw = w / 2;
                }
                case RIGHT -> {
                    hx = x + w / 2;
                    hw = w / 2;
                }
            }
            g.setFill(Color.color(1, 1, 0, 0.25));
            g.fillRect(hx, hy, hw, hh);
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2);
            g.strokeRect(hx, hy, hw, hh);
        });

        // Triangle: RIGHT-angled; LEFT/RIGHT are 90° rotations of UP/DOWN
        STRATS.put(ShapeType.TRIANGLE, (g, x, y, w, h, o) -> {
            double[] xs = {}, ys = {};
            switch (o) {
                // UP: right angle at top-left; hypotenuse to bottom-right
                case UP_LEFT -> {
                    xs = new double[]{x, x + w, x};
                    ys = new double[]{y, y, y + h};
                }
                // DOWN: 180° rotate UP
                case UP_RIGHT -> {
                    xs = new double[]{x + w, x, x + w};
                    ys = new double[]{y + h, y + h, y};
                }
                // LEFT: 90° CCW rotate UP
                case DOWN_RIGHT -> {
                    xs = new double[]{x, x + w, x + w};
                    ys = new double[]{y, y, y + h};
                }
                case UP -> {
                    xs = new double[]{x, x + w, x};
                    ys = new double[]{y, y, y + h};
                }
                // DOWN: 180° rotate UP
                case DOWN -> {
                    xs = new double[]{x, x, x + w};
                    ys = new double[]{y + h, y + h, y};
                }
                // LEFT: 90° CCW rotate UP
                case LEFT -> {
                    xs = new double[]{x, x, x + w};
                    ys = new double[]{y, y, y + h};
                }
                // RIGHT: 90° CW rotate UP
                case RIGHT -> {
                    xs = new double[]{x, x, x + w};
                    ys = new double[]{y, y + h, y + h};
                }
                // RIGHT: 90° CW rotate UP
                case DOWN_LEFT -> {
                    xs = new double[]{x, x, x + w};
                    ys = new double[]{y + h, y, y + h};
                }
            }
            g.setFill(Color.color(1, 1, 0, 0.25));
            g.fillPolygon(xs, ys, 3);
            g.setStroke(Color.color(1, 1, 0, 0.9));
            g.setLineWidth(2);
            g.strokePolygon(xs, ys, 3);
        });
    }

    public static CollisionShapeStrategy get(ShapeType t) {
        return STRATS.getOrDefault(t, STRATS.get(ShapeType.RECT_FULL));
    }
}
