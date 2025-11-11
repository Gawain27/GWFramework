package com.gw.map.ui.sidebar;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Right-side content for PLANE tab: placeholder + Edit stub. */
public class PlaneSidebarPane extends VBox {
    public final Button editBtn = new Button("Edit...");
    public PlaneSidebarPane() {
        setPadding(new Insets(10)); setSpacing(12);
        getChildren().addAll(new Label("Plane Tools"), editBtn);
    }
}
