package com.gw.map.ui.sidebar;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Right sidebar for the 3D map view.
 * Controls:
 * - Base plane (X=x=k, Y=y=k, Z=z=k)
 * - Plane index display + prev/next
 * - Zoom in/out
 * - Edit current plane
 * - Show/hide grid (all plane lines)
 */
public class MapSidebarPane extends VBox {

    public final Button prevPlaneBtn = new Button("<");
    public final Button nextPlaneBtn = new Button(">");
    public final Button zoomInBtn = new Button("+");
    public final Button zoomOutBtn = new Button("−");
    public final Button editPlaneBtn = new Button("Edit plane");
    private final ComboBox<UiBasePlane> basePlaneCombo = new ComboBox<>();
    private final Label planeIndexLabel = new Label("Plane index: - / -");
    private final CheckBox showGridCheck = new CheckBox("Show grid");
    // Callbacks wired by MapEditorPane
    private Consumer<UiBasePlane> onBasePlaneChanged;
    private Runnable onPrevPlane;
    private Runnable onNextPlane;
    private Runnable onZoomIn;
    private Runnable onZoomOut;
    private Runnable onEditCurrentPlane;
    private Consumer<Boolean> onShowGridChanged;
    public MapSidebarPane() {
        setSpacing(8);
        setPadding(new Insets(10));
        setFillWidth(true);

        getChildren().add(buildBasePlaneBox());
        getChildren().add(buildPlaneIndexBox());
        getChildren().add(buildZoomBox());
        getChildren().add(buildEditBox());
        getChildren().add(buildGridToggleBox());

        // defaults
        basePlaneCombo.getItems().addAll(UiBasePlane.X, UiBasePlane.Y, UiBasePlane.Z);
        basePlaneCombo.getSelectionModel().select(UiBasePlane.Z);
        showGridCheck.setSelected(true);

        basePlaneCombo.setOnAction(e -> {
            UiBasePlane sel = basePlaneCombo.getValue();
            if (sel != null && onBasePlaneChanged != null) {
                onBasePlaneChanged.accept(sel);
            }
        });

        prevPlaneBtn.setOnAction(e -> {
            if (onPrevPlane != null) onPrevPlane.run();
        });
        nextPlaneBtn.setOnAction(e -> {
            if (onNextPlane != null) onNextPlane.run();
        });

        zoomInBtn.setOnAction(e -> {
            if (onZoomIn != null) onZoomIn.run();
        });
        zoomOutBtn.setOnAction(e -> {
            if (onZoomOut != null) onZoomOut.run();
        });

        editPlaneBtn.setOnAction(e -> {
            if (onEditCurrentPlane != null) onEditCurrentPlane.run();
        });

        showGridCheck.selectedProperty().addListener((obs, ov, nv) -> {
            if (onShowGridChanged != null) onShowGridChanged.accept(nv);
        });
    }

    private HBox buildBasePlaneBox() {
        Label lbl = new Label("Base plane:");
        HBox box = new HBox(6, lbl, basePlaneCombo);
        box.setAlignment(Pos.CENTER_LEFT);
        TitledPane tp = new TitledPane("Plane selection", box);
        tp.setCollapsible(false);
        return new HBox(tp); // just to keep a Node wrapper; VBox will stretch it
    }

    /* ------------ Small layout helpers ------------ */

    private TitledPane buildPlaneIndexBox() {
        HBox row = new HBox(6, new Label("Index:"), prevPlaneBtn, planeIndexLabel, nextPlaneBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        TitledPane tp = new TitledPane("Plane index", row);
        tp.setCollapsible(false);
        return tp;
    }

    private TitledPane buildZoomBox() {
        HBox row = new HBox(6, zoomOutBtn, zoomInBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        TitledPane tp = new TitledPane("Zoom", row);
        tp.setCollapsible(false);
        return tp;
    }

    private TitledPane buildEditBox() {
        HBox row = new HBox(6, editPlaneBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        TitledPane tp = new TitledPane("Plane editor", row);
        tp.setCollapsible(false);
        return tp;
    }

    private TitledPane buildGridToggleBox() {
        HBox row = new HBox(6, showGridCheck);
        row.setAlignment(Pos.CENTER_LEFT);
        TitledPane tp = new TitledPane("Display", row);
        tp.setCollapsible(false);
        return tp;
    }

    public void setOnBasePlaneChanged(Consumer<UiBasePlane> handler) {
        this.onBasePlaneChanged = handler;
    }

    /* ------------ API for MapEditorPane ------------ */

    public void setOnPrevPlane(Runnable r) {
        this.onPrevPlane = r;
    }

    public void setOnNextPlane(Runnable r) {
        this.onNextPlane = r;
    }

    public void setOnZoomIn(Runnable r) {
        this.onZoomIn = r;
    }

    public void setOnZoomOut(Runnable r) {
        this.onZoomOut = r;
    }

    public void setOnEditCurrentPlane(Runnable r) {
        this.onEditCurrentPlane = r;
    }

    public void setOnShowGridChanged(Consumer<Boolean> handler) {
        this.onShowGridChanged = handler;
    }

    public void setPlaneIndexText(String text) {
        planeIndexLabel.setText(text);
    }

    public void setSelectedBasePlane(UiBasePlane plane) {
        if (plane == null) return;
        basePlaneCombo.getSelectionModel().select(plane);
    }

    public void setShowGrid(boolean show) {
        showGridCheck.setSelected(show);
    }

    public enum UiBasePlane {X, Y, Z}
}
