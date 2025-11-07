package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateDef.Orientation;
import com.gw.editor.template.TemplateDef.ShapeType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

public class TilePropertiesPane extends VBox {

    private final Map<Point, TemplateDef.TileDef> staged = new HashMap<>();
    private TemplateDef boundTemplate;

    private final Label hdr = new Label("Tile Properties");

    private final TextField tagField = new TextField();
    private final CheckBox solidBox = new CheckBox("Solid (has collision)");
    private final ComboBox<ShapeType> shapeBox = new ComboBox<>();
    private final ComboBox<Orientation> orientBox = new ComboBox<>();
    private final Spinner<Double> floatSpinner = new Spinner<>(0.0, 10.0, 0.0, 0.1);

    private int curGx = -1, curGy = -1;

    private Runnable editsChanged; // callback to repaint overlays, etc.

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

        shapeBox.disableProperty().bind(solidBox.selectedProperty().not());
        orientBox.disableProperty().bind(solidBox.selectedProperty().not());

        getChildren().addAll(hdr, tagBox, solidBoxWrap, shapeWrap, orientWrap, floatBox);

        tagField.textProperty().addListener((obs, o, n) -> stageIfActive());
        solidBox.selectedProperty().addListener((obs, o, n) -> stageIfActive());
        shapeBox.valueProperty().addListener((obs, o, n) -> stageIfActive());
        orientBox.valueProperty().addListener((obs, o, n) -> stageIfActive());
        floatSpinner.valueProperty().addListener((obs, o, n) -> stageIfActive());
    }

    public void bindTo(TemplateDef t) {
        this.boundTemplate = t;
        clearEdits();
        clearUI();
    }

    public void setEditsChangedCallback(Runnable r) {
        this.editsChanged = r;
    }

    public void showTile(int gx, int gy) {
        this.curGx = gx;
        this.curGy = gy;
        if (gx < 0 || gy < 0) {
            clearUI();
            return;
        }

        TemplateDef.TileDef src = getEffectiveTile(gx, gy);

        if (src == null) {
            tagField.setText("");
            solidBox.setSelected(false);
            shapeBox.setValue(ShapeType.RECT_FULL);
            orientBox.setValue(Orientation.UP);
            floatSpinner.getValueFactory().setValue(0.0);
        } else {
            tagField.setText(src.tag == null ? "" : src.tag);
            solidBox.setSelected(src.solid);
            shapeBox.setValue(src.shape == null ? ShapeType.RECT_FULL : src.shape);
            orientBox.setValue(src.orientation == null ? Orientation.UP : src.orientation);
            floatSpinner.getValueFactory().setValue((double) src.customFloat);
        }
    }

    public TemplateDef.TileDef getEffectiveTile(int gx, int gy) {
        TemplateDef.TileDef stagedTile = staged.get(new Point(gx, gy));
        if (stagedTile != null) return stagedTile;
        if (boundTemplate == null) return null;
        return boundTemplate.tileAt(gx, gy);
    }

    public void clearEdits() {
        staged.clear();
        curGx = curGy = -1;
    }

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

    private void stageIfActive() {
        if (curGx < 0 || curGy < 0) return;
        TemplateDef.TileDef copy = new TemplateDef.TileDef(curGx, curGy);
        copy.tag = tagField.getText();
        copy.customFloat = floatSpinner.getValue().floatValue();
        copy.solid = solidBox.isSelected();
        copy.shape = shapeBox.getValue();
        copy.orientation = orientBox.getValue();
        staged.put(new Point(curGx, curGy), copy);

        if (editsChanged != null) editsChanged.run();
    }
}
