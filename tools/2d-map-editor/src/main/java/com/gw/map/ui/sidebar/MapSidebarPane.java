package com.gw.map.ui.sidebar;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Right sidebar: Map tab (base plane, plane index, zoom, edit).
 */
public class MapSidebarPane extends VBox {

    private final ComboBox<UiBasePlane> basePlaneCombo = new ComboBox<>();
    private final Button prevPlaneBtn = new Button("◀");
    private final Button nextPlaneBtn = new Button("▶");
    private final Label planeIndexLabel = new Label("-");
    private final Button zoomInBtn = new Button("+");
    private final Button zoomOutBtn = new Button("−");
    private final Button editBtn = new Button("Edit");
    // callbacks
    private java.util.function.Consumer<UiBasePlane> onBasePlaneChanged = bp -> {
    };
    private Runnable onPrevPlane = () -> {
    };
    private Runnable onNextPlane = () -> {
    };
    private Runnable onZoomIn = () -> {
    };
    private Runnable onZoomOut = () -> {
    };
    private Runnable onEdit = () -> {
    };
    public MapSidebarPane() {
        setSpacing(8);
        setPadding(new Insets(10));
        setFillWidth(true);

        getChildren().addAll(
            buildBasePlaneRow(),
            buildPlaneIndexRow(),
            buildZoomRow(),
            editBtn
        );

        basePlaneCombo.getItems().setAll(UiBasePlane.values());
        basePlaneCombo.getSelectionModel().select(UiBasePlane.X); // default
        basePlaneCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) onBasePlaneChanged.accept(nv);
        });

        prevPlaneBtn.setOnAction(e -> onPrevPlane.run());
        nextPlaneBtn.setOnAction(e -> onNextPlane.run());
        zoomInBtn.setOnAction(e -> onZoomIn.run());
        zoomOutBtn.setOnAction(e -> onZoomOut.run());
        editBtn.setOnAction(e -> onEdit.run());
    }

    private Node buildBasePlaneRow() {
        HBox row = new HBox(8, new Label("Base plane:"), basePlaneCombo);
        return row;
    }

    private Node buildPlaneIndexRow() {
        HBox row = new HBox(8, new Label("Index:"), prevPlaneBtn, planeIndexLabel, nextPlaneBtn);
        return row;
    }

    private Node buildZoomRow() {
        HBox row = new HBox(8, new Label("Zoom:"), zoomOutBtn, zoomInBtn);
        return row;
    }

    // API for MapEditorPane
    public void setPlaneIndexText(String text) {
        planeIndexLabel.setText(text);
    }

    public void setSelectedBasePlane(UiBasePlane ui) {
        basePlaneCombo.getSelectionModel().select(ui);
    }

    public void setOnBasePlaneChanged(java.util.function.Consumer<UiBasePlane> cb) {
        this.onBasePlaneChanged = (cb != null) ? cb : (x -> {
        });
    }

    public void setOnPrevPlane(Runnable r) {
        this.onPrevPlane = (r != null) ? r : () -> {
        };
    }

    public void setOnNextPlane(Runnable r) {
        this.onNextPlane = (r != null) ? r : () -> {
        };
    }

    public void setOnZoomIn(Runnable r) {
        this.onZoomIn = (r != null) ? r : () -> {
        };
    }

    public void setOnZoomOut(Runnable r) {
        this.onZoomOut = (r != null) ? r : () -> {
        };
    }

    public void setOnEditCurrentPlane(Runnable r) {
        this.onEdit = (r != null) ? r : () -> {
        };
    }

    public enum UiBasePlane {X, Y, Z} // UI labels
}
