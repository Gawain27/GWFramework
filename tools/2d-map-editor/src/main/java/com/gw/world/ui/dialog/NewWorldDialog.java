package com.gw.world.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

/**
 * Simple dialog to create a new world: name + X/Y/Z dimensions.
 */
public class NewWorldDialog extends Dialog<NewWorldDialog.Result> {

    public record Result(String name, int widthX, int heightY, int depthZ) {}

    private final TextField nameField = new TextField();
    private final Spinner<Integer> widthSpinner = new Spinner<>(1, 512, 64, 1);
    private final Spinner<Integer> heightSpinner = new Spinner<>(1, 256, 32, 1);
    private final Spinner<Integer> depthSpinner = new Spinner<>(1, 512, 64, 1);

    public NewWorldDialog() {
        setTitle("New World");

        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.setPadding(new Insets(10));

        int r = 0;
        gp.add(new Label("Name:"), 0, r);
        gp.add(nameField, 1, r++);

        gp.add(new Label("Width (X):"), 0, r);
        gp.add(widthSpinner, 1, r++);

        gp.add(new Label("Height (Y):"), 0, r);
        gp.add(heightSpinner, 1, r++);

        gp.add(new Label("Depth (Z):"), 0, r);
        gp.add(depthSpinner, 1, r++);

        getDialogPane().setContent(gp);

        // Disable Create if name is blank
        var createBtn = getDialogPane().lookupButton(createType);
        createBtn.disableProperty().bind(nameField.textProperty().isEmpty());

        setResultConverter(btn -> {
            if (btn == createType) {
                String name = nameField.getText().trim();
                int w = widthSpinner.getValue();
                int h = heightSpinner.getValue();
                int d = depthSpinner.getValue();
                return new Result(name, w, h, d);
            }
            return null;
        });
    }
}
