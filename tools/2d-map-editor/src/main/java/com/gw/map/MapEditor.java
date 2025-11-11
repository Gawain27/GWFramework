package com.gw.map;

import com.gw.editor.template.TemplateRepository;
import com.gw.map.io.MapRepository;
import com.gw.map.model.MapDef;
import com.gw.map.ui.MapEditorPane;
import com.gw.map.ui.TemplateGalleryPane;
import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;


/**
 * Entry point: wires left gallery and center editor.
 */
public class MapEditor extends Application {

    @Override
    public void start(Stage stage) {
        TemplateRepository templateRepository = new TemplateRepository();
        MapRepository mapRepo = new MapRepository();
        MapEditorPane editorPane = new MapEditorPane(mapRepo);
        TemplateGalleryPane gallery = new TemplateGalleryPane(templateRepository);

        SplitPane split = new SplitPane(gallery, editorPane);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.22);
        Scene scene = new Scene(split, 1080, 640);
        stage.setTitle("3D Grid Map Editor — MVP v2");
        stage.setScene(scene);
        stage.show();
        editorPane.setMap(MapDef.createDefault());
    }

    public static void main(String[] args) { launch(args); }
}
