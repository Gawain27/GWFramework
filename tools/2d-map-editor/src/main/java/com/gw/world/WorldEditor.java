package com.gw.world;

import com.gw.editor.template.TemplateRepository;
import com.gw.map.io.MapRepository;
import com.gw.world.io.WorldRepository;
import com.gw.world.ui.WorldEditorPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WorldEditor extends Application {

    @Override
    public void start(Stage stage) {
        TemplateRepository templateRepository = new TemplateRepository();
        MapRepository mapRepo = new MapRepository();
        WorldRepository worldRepo = new WorldRepository();

        WorldEditorPane root = new WorldEditorPane(worldRepo, mapRepo, templateRepository);

        Scene scene = new Scene(root, 1080, 640);
        stage.setTitle("3D World Editor - MVP");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
