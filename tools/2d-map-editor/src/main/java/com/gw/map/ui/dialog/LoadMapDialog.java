package com.gw.map.ui.dialog;

import com.gw.map.io.MapRepository;
import com.gw.map.model.MapDef;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Simple list popup to search and select a saved map.
 */
public class LoadMapDialog extends Dialog<MapDef> {
    private final TextField search = new TextField();
    private final ListView<Path> list = new ListView<>();

    public LoadMapDialog(MapRepository repo) {
        setTitle("Load Map");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        BorderPane root = new BorderPane();
        search.setPromptText("Search by filename...");
        root.setTop(search);
        root.setCenter(list);
        BorderPane.setMargin(search, new Insets(8));
        BorderPane.setMargin(list, new Insets(8));
        getDialogPane().setContent(root);
        ObservableList<Path> items = FXCollections.observableArrayList(repo.listJsonFiles());
        list.setItems(items);
        search.textProperty().addListener((obs, o, n) -> {
            String q = n == null ? "" : n.trim().toLowerCase();
            list.setItems(FXCollections.observableArrayList(repo.listJsonFiles().stream().filter(p -> p.getFileName().toString().toLowerCase().contains(q)).collect(Collectors.toList())));
        });
        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Path sel = list.getSelectionModel().getSelectedItem();
            return sel == null ? null : repo.load(sel);
        });
    }
}
