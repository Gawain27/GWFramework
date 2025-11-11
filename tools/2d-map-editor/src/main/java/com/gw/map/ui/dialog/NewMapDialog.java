package com.gw.map.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.UUID;

/**
 * Dialog to create a new map with fixed size and optional background template.
 * If a background is chosen, width/depth are inferred from template (tile size aware) but height remains editable.
 */
public class NewMapDialog extends Dialog<NewMapDialog.Result> {
    private final TextField name = new TextField("New Map");
    private final TextField id = new TextField("map-" + UUID.randomUUID().toString().substring(0, 8));
    private final Spinner<Integer> width = new Spinner<>(1, 1024, 16);
    private final Spinner<Integer> height = new Spinner<>(1, 1024, 16);
    private final Spinner<Integer> depth = new Spinner<>(1, 1024, 6);

    public NewMapDialog() {
        setTitle("New Map");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.setPadding(new Insets(12));
        gp.addRow(0, new Label("Name"), name);
        gp.addRow(1, new Label("Id"), id);
        gp.addRow(2, new Label("Width X"), width);
        gp.addRow(3, new Label("Height Y"), height);
        gp.addRow(4, new Label("Depth Z"), depth);
        getDialogPane().setContent(gp);
        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(id.textProperty().isEmpty().or(name.textProperty().isEmpty()));
        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Result r = new Result();
            r.mapId = id.getText().trim();
            r.mapName = name.getText().trim();
            r.widthX = width.getValue();
            r.heightY = height.getValue();
            r.depthZ = depth.getValue();
            return r;
        });
    }

    public static class Result {
        public String mapId, mapName;
        public int widthX, heightY, depthZ;
    }
}
