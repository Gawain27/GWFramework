package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateDef.Orientation;
import com.gw.editor.template.TemplateDef.ShapeType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.awt.Point;
import java.util.*;
import java.util.stream.Collectors;

public class TilePropertiesPane extends VBox {

    private final Map<Point, TemplateDef.TileDef> staged = new HashMap<>();
    private TemplateDef boundTemplate;

    private final Label hdr = new Label("Tile Properties");

    private final TextField tagField = new TextField();
    private final CheckBox solidBox = new CheckBox("Solid (has collision)");
    private final ComboBox<ShapeType> shapeBox = new ComboBox<>();
    private final ComboBox<Orientation> orientBox = new ComboBox<>();
    private final Spinner<Double> floatSpinner = new Spinner<>(0.0, 10.0, 0.0, 0.1);

    private final Set<Point> selection = new LinkedHashSet<>();

    private Runnable editsChanged; // notify viewer to repaint

    public TilePropertiesPane() {
        setSpacing(8);
        setPadding(new Insets(8));

        shapeBox.getItems().setAll(ShapeType.values());
        shapeBox.setValue(ShapeType.RECT_FULL);
        orientBox.getItems().setAll(Orientation.values());
        orientBox.setValue(Orientation.UP);

        var tagBox = new VBox(4, tagField, new Label("Tag"));
        var solidBoxWrap = new VBox(4, solidBox, new Label("Collision enabled"));
        var shapeWrap = new VBox(4, shapeBox, new Label("Shape"));
        var orientWrap = new VBox(4, orientBox, new Label("Orientation"));
        var floatBox = new VBox(4, floatSpinner, new Label("Custom"));

        // Tag/custom disabled when multi-select
        tagField.disableProperty().bind(new javafx.beans.binding.BooleanBinding() {
            {
                bind();
            }

            @Override
            protected boolean computeValue() {
                return selection.size() > 1;
            }
        });
        floatSpinner.disableProperty().bind(tagField.disableProperty());

        // Shape/orientation disabled when not solid
        shapeBox.disableProperty().bind(solidBox.selectedProperty().not());
        orientBox.disableProperty().bind(solidBox.selectedProperty().not());

        getChildren().addAll(hdr, tagBox, solidBoxWrap, shapeWrap, orientWrap, floatBox);

        // listeners – stage without touching template
        tagField.textProperty().addListener((obs, o, n) -> stageForSelection(true, false));
        floatSpinner.valueProperty().addListener((obs, o, n) -> stageForSelection(true, false));
        solidBox.selectedProperty().addListener((obs, o, n) -> stageForSelection(false, true));
        shapeBox.valueProperty().addListener((obs, o, n) -> stageForSelection(false, true));
        orientBox.valueProperty().addListener((obs, o, n) -> stageForSelection(false, true));
    }

    public void bindTo(TemplateDef t) {
        this.boundTemplate = t;
        clearEdits();
        selection.clear();
        clearUI();
    }

    public void setEditsChangedCallback(Runnable r) {
        this.editsChanged = r;
    }

    /**
     * Viewer provides the current selection (single or multi).
     */
    public void showSelection(Set<Point> sel) {
        selection.clear();
        if (sel != null) selection.addAll(sel);

        if (selection.isEmpty()) {
            clearUI();
            return;
        }

        if (selection.size() == 1) {
            Point p = selection.iterator().next();
            TemplateDef.TileDef src = getEffectiveTile(p.x, p.y);
            if (src == null) {
                tagField.setText("");
                floatSpinner.getValueFactory().setValue(0.0);
                solidBox.setSelected(false);
                shapeBox.setValue(ShapeType.RECT_FULL);
                orientBox.setValue(Orientation.UP);
            } else {
                tagField.setText(src.tag == null ? "" : src.tag);
                floatSpinner.getValueFactory().setValue((double) src.customFloat);
                solidBox.setSelected(src.solid);
                shapeBox.setValue(src.shape == null ? ShapeType.RECT_FULL : src.shape);
                orientBox.setValue(src.orientation == null ? Orientation.UP : src.orientation);
            }
        } else {
            // multi: show majority/common (simple heuristic)
            var tiles = selection.stream().map(p -> getEffectiveTile(p.x, p.y)).filter(Objects::nonNull).toList();
            boolean any = !tiles.isEmpty();
            boolean allSolid = any && tiles.stream().allMatch(t -> t.solid);
            boolean anySolid = tiles.stream().anyMatch(t -> t.solid);

            solidBox.setSelected(allSolid); // if not all, will flip on next user click
            shapeBox.setValue(tiles.isEmpty() ? ShapeType.RECT_FULL
                : tiles.get(0).shape == null ? ShapeType.RECT_FULL : tiles.get(0).shape);
            orientBox.setValue(tiles.isEmpty() ? Orientation.UP
                : tiles.get(0).orientation == null ? Orientation.UP : tiles.get(0).orientation);

            // tag/custom fields disabled by binding above; show blank
            tagField.setText("");
            floatSpinner.getValueFactory().setValue(0.0);
        }
    }

    /**
     * Returns staged version if present, otherwise template version; null if none.
     */
    public TemplateDef.TileDef getEffectiveTile(int gx, int gy) {
        TemplateDef.TileDef stagedTile = staged.get(new Point(gx, gy));
        if (stagedTile != null) return stagedTile;
        if (boundTemplate == null) return null;
        return boundTemplate.tileAt(gx, gy);
    }

    public void clearEdits() {
        staged.clear();
    }

    /**
     * Merge staged edits into the target template (called on Save).
     */
    public void applyEditsTo(TemplateDef target) {
        if (target == null) return;
        for (var e : staged.entrySet()) {
            int gx = e.getKey().x, gy = e.getKey().y;
            TemplateDef.TileDef src = e.getValue();
            TemplateDef.TileDef dst = target.ensureTile(gx, gy);

            dst.tag = src.tag;
            dst.customFloat = src.customFloat;
            dst.solid = src.solid;
            dst.shape = src.shape;
            dst.orientation = src.orientation;
        }
    }

    private void clearUI() {
        tagField.setText("");
        solidBox.setSelected(false);
        shapeBox.setValue(ShapeType.RECT_FULL);
        orientBox.setValue(Orientation.UP);
        floatSpinner.getValueFactory().setValue(0.0);
    }

    /**
     * Stage edits for current selection.
     *
     * @param singleOnly    true => apply only if single-selected (tag/custom)
     * @param collisionOnly true => apply only Solid/Shape/Orientation (multi-friendly)
     */
    private void stageForSelection(boolean singleOnly, boolean collisionOnly) {
        if (selection.isEmpty()) return;

        boolean isMulti = selection.size() > 1;
        if (singleOnly && isMulti) return; // avoid applying tag/custom to multiple

        for (Point p : selection) {
            TemplateDef.TileDef copy = Optional.ofNullable(staged.get(p))
                .map(t -> {
                    TemplateDef.TileDef c = new TemplateDef.TileDef(p.x, p.y);
                    c.tag = t.tag;
                    c.customFloat = t.customFloat;
                    c.solid = t.solid;
                    c.shape = t.shape;
                    c.orientation = t.orientation;
                    return c;
                })
                .orElseGet(() -> {
                    TemplateDef.TileDef base = getEffectiveTile(p.x, p.y);
                    TemplateDef.TileDef c = new TemplateDef.TileDef(p.x, p.y);
                    if (base != null) {
                        c.tag = base.tag;
                        c.customFloat = base.customFloat;
                        c.solid = base.solid;
                        c.shape = base.shape;
                        c.orientation = base.orientation;
                    }
                    return c;
                });

            if (!collisionOnly) {
                copy.tag = tagField.getText();
                copy.customFloat = floatSpinner.getValue().floatValue();
            }
            // always set collision when requested (single or multi)
            if (collisionOnly || !isMulti) {
                copy.solid = solidBox.isSelected();
                copy.shape = shapeBox.getValue();
                copy.orientation = orientBox.getValue();
            }

            staged.put(new Point(p), copy);
        }

        if (editsChanged != null) editsChanged.run();
    }
}
