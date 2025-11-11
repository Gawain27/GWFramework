package com.gw.map.ui.sidebar;

import com.gw.map.model.SelectionState;
import com.gw.map.ui.MapEditorPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Right-side content for MAP tab: base-plane, plane index navigation, zoom
 * controls.
 */
public class MapSidebarPane extends VBox {
    public final ComboBox<SelectionState.BasePlane> basePlaneCombo = new ComboBox<>();
    public final Label planeIndexLabel = new Label();
    public final Button prevPlaneBtn = new Button("Prev plane");
    public final Button nextPlaneBtn = new Button("Next plane");
    public final Button zoomInBtn = new Button("Zoom +");
    public final Button zoomOutBtn = new Button("Zoom -");

    public MapSidebarPane(MapEditorPane editor) {
        setPadding(new Insets(10));
        setSpacing(12);
        getChildren().add(new Label("Map Controls"));
        basePlaneCombo.getItems().addAll(SelectionState.BasePlane.values());
        basePlaneCombo.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(new HBox(6, new Label("Base plane:"), basePlaneCombo));
        HBox planeNav = new HBox(6, prevPlaneBtn, planeIndexLabel, nextPlaneBtn);
        planeNav.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(planeNav);
        HBox zoom = new HBox(6, zoomOutBtn, zoomInBtn);
        getChildren().add(zoom);
    }
}
