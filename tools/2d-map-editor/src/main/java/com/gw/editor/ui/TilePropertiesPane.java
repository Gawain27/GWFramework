package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

public class TilePropertiesPane extends VBox {

    // simple staged edits: gx,gy -> TileDef snapshot
    private final Map<Point, TemplateDef.TileDef> staged = new HashMap<>();
    private TemplateDef boundTemplate;

    private final Label hdr = new Label("Tile Properties");
    private final TextField tagField = new TextField();
    private final CheckBox solidBox = new CheckBox("Solid");
    private final Spinner<Double> floatSpinner = new Spinner<>(0.0, 10.0, 0.0, 0.1);

    private int curGx = -1, curGy = -1;

    public TilePropertiesPane() {
        setSpacing(8);
        setPadding(new Insets(8));

        // labels under the controls (as requested)
        var tagBox = new VBox(4, tagField, new Label("Tag"));
        var solidBoxWrap = new VBox(4, solidBox, new Label("Collision"));
        var floatBox = new VBox(4, floatSpinner, new Label("Custom"));

        getChildren().addAll(hdr, tagBox, solidBoxWrap, floatBox);

        tagField.textProperty().addListener((obs, o, n) -> stageIfActive());
        solidBox.selectedProperty().addListener((obs, o, n) -> stageIfActive());
        floatSpinner.valueProperty().addListener((obs, o, n) -> stageIfActive());
    }

    public void bindTo(TemplateDef t) {
        this.boundTemplate = t;
        clearEdits();
        clearUI();
    }

    public void showTile(int gx, int gy) {
        this.curGx = gx;
        this.curGy = gy;
        if (gx < 0 || gy < 0) {
            clearUI();
            return;
        }

        TemplateDef.TileDef src = staged.get(new Point(gx, gy));
        if (src == null && boundTemplate != null) src = boundTemplate.tileAt(gx, gy);

        if (src == null) {
            tagField.setText("");
            solidBox.setSelected(false);
            floatSpinner.getValueFactory().setValue(0.0);
        } else {
            tagField.setText(src.tag == null ? "" : src.tag);
            solidBox.setSelected(src.solid);
            floatSpinner.getValueFactory().setValue((double) src.customFloat);
        }
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
            dst.solid = src.solid;
            dst.customFloat = src.customFloat;
        }
    }

    private void clearUI() {
        tagField.setText("");
        solidBox.setSelected(false);
        floatSpinner.getValueFactory().setValue(0.0);
    }

    private void stageIfActive() {
        if (curGx < 0 || curGy < 0) return;
        TemplateDef.TileDef copy = new TemplateDef.TileDef(curGx, curGy);
        copy.tag = tagField.getText();
        copy.solid = solidBox.isSelected();
        copy.customFloat = floatSpinner.getValue().floatValue();
        staged.put(new Point(curGx, curGy), copy);
    }
}
