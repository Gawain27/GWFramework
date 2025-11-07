package com.gw.editor;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.ui.GridOverlayPane;
import com.gw.editor.ui.TilePropertiesPane;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.util.Cdi;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public class FxEditorApp extends Application {
    private TemplateRepository repo;
    private GridOverlayPane viewer;
    private TextField filterField;

    private Spinner<Integer> tileW;
    private Spinner<Integer> tileH;
    private TextField templateIdField;

    private FilteredList<AssetScanner.AssetEntry> filteredAssets;
    private ListView<Path> templatesList;

    private TemplateDef current;
    private TilePropertiesPane tilePropsPane;

    @Inject
    private IAssetManager manager;

    @Override
    public void start(Stage stage) {
        Cdi.inject(this);
        repo = new TemplateRepository();

        TabPane sidebar = new TabPane();
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebar.getTabs().add(new Tab("Assets", buildAssetsPane()));
        sidebar.getTabs().add(new Tab("Templates", buildTemplatesPane()));

        viewer = new GridOverlayPane();
        // ✅ Do NOT bind the properties pane on each click (keeps staged edits)
        viewer.setTileClickHandler((gx, gy) -> {
            if (current == null) return;
            tilePropsPane.showTile(gx, gy);
        });
        viewer.setCollisionProvider((gx, gy) -> {
            if (tilePropsPane == null) return null;
            return tilePropsPane.getEffectiveTile(gx, gy);
        });

        BorderPane center = new BorderPane();
        center.setTop(buildTopControls());
        center.setCenter(viewer);

        tilePropsPane = new TilePropertiesPane();
        tilePropsPane.setMinWidth(260);
        // ✅ When any staged edit changes, refresh the overlay
        tilePropsPane.setEditsChangedCallback(() -> viewer.refresh());

        SplitPane root = new SplitPane(sidebar, center, tilePropsPane);
        root.setOrientation(Orientation.HORIZONTAL);
        root.setDividerPositions(0.25, 0.80);
        SplitPane.setResizableWithParent(sidebar, true);
        SplitPane.setResizableWithParent(center, true);
        SplitPane.setResizableWithParent(tilePropsPane, true);

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("GW Template Editor (JavaFX)");
        stage.setScene(scene);
        stage.show();

        refreshAssets();
        refreshTemplates();

        filteredAssets.setPredicate(e -> {
            String n = filterField.getText();
            return n == null || n.isBlank() || e.logicalPath().toLowerCase().contains(n.toLowerCase());
        });
    }

    private VBox buildTopControls() {
        templateIdField = new TextField();
        templateIdField.setPromptText("crate_small");
        VBox idBox = new VBox(4, templateIdField, new Label("Template Id"));
        idBox.setAlignment(Pos.CENTER_LEFT);

        tileW = new Spinner<>(1, 1024, 16, 1);
        tileH = new Spinner<>(1, 1024, 16, 1);
        viewer.tileWidthProperty().bind(tileW.valueProperty());
        viewer.tileHeightProperty().bind(tileH.valueProperty());
        VBox wBox = new VBox(4, tileW, new Label("Tile W (px)"));
        VBox hBox = new VBox(4, tileH, new Label("Tile H (px)"));

        Button newBtn = new Button("New");
        newBtn.setOnAction(e -> newTemplate());

        Button saveBtn = new Button("Save");
        saveBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> current == null || templateIdField.getText().isBlank(),
            templateIdField.textProperty()
        ));
        saveBtn.setOnAction(e -> doSave());

        Button deleteBtn = new Button("Delete");
        deleteBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> current == null || current.id == null || current.id.isBlank(),
            templateIdField.textProperty()
        ));
        deleteBtn.setOnAction(e -> doDelete());

        ToolBar toolbar = new ToolBar(newBtn, saveBtn, deleteBtn);

        HBox underBar = new HBox(16, idBox, wBox, hBox);
        underBar.setPadding(new Insets(8));
        underBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(toolbar, underBar);
        top.setFillWidth(true);
        return top;
    }

    private VBox buildAssetsPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        filterField = new TextField();
        filterField.setPromptText("Filter assets by name…");

        TilePane grid = new TilePane(10, 10);
        grid.setPrefColumns(3);
        grid.setPadding(new Insets(4));
        grid.setPrefTileWidth(260);
        grid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);

        filteredAssets = new FilteredList<>(FXCollections.observableArrayList());
        filterField.textProperty().addListener((obs, o, n) -> {
            Predicate<AssetScanner.AssetEntry> p = e -> n == null || n.isBlank() || e.logicalPath().toLowerCase().contains(n.toLowerCase());
            filteredAssets.setPredicate(p);
            renderAssetTiles(grid);
        });

        renderAssetTiles(grid);

        box.getChildren().addAll(filterField, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        return box;
    }

    private void renderAssetTiles(TilePane grid) {
        grid.getChildren().setAll();
        for (AssetScanner.AssetEntry e : filteredAssets) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(6));
            card.setAlignment(Pos.TOP_CENTER);
            card.setPrefWidth(260);

            ImageView iv = new ImageView(e.thumbnail());
            iv.setPreserveRatio(true);
            iv.setFitWidth(240);
            iv.setOnMouseClicked(me -> {
                if (me.getButton() == MouseButton.PRIMARY) onAssetSelected(e);
            });

            Label name = new Label(e.logicalPath());
            name.setWrapText(true);
            name.setMaxWidth(240);

            card.getChildren().addAll(iv, name);
            grid.getChildren().add(card);
        }
    }

    private VBox buildTemplatesPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        templatesList = new ListView<>();
        templatesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Path p = templatesList.getSelectionModel().getSelectedItem();
                if (p != null) loadTemplate(p);
            }
        });

        HBox actions = new HBox(8);
        Button load = new Button("Load");
        load.setOnAction(e -> {
            Path p = templatesList.getSelectionModel().getSelectedItem();
            if (p != null) loadTemplate(p);
        });
        Button del = new Button("Delete");
        del.setOnAction(e -> {
            Path p = templatesList.getSelectionModel().getSelectedItem();
            if (p != null) {
                String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                repo.delete(id);
                refreshTemplates();
            }
        });
        actions.getChildren().addAll(load, del);

        box.getChildren().addAll(templatesList, actions);
        VBox.setVgrow(templatesList, Priority.ALWAYS);
        return box;
    }

    private void newTemplate() {
        current = new TemplateDef();
        current.id = "";
        templateIdField.setText("");
        tilePropsPane.bindTo(current); // reset staged edits (only on NEW)
        viewer.getImageView().setImage(null);
        viewer.refresh();
    }

    private void onAssetSelected(AssetScanner.AssetEntry e) {
        if (current == null) {
            current = new TemplateDef();
            tilePropsPane.bindTo(current); // bind only once when template is created
        }
        current.logicalPath = e.logicalPath();
        templateIdField.setText(suggestIdFromPath(e.logicalPath()));

        viewer.getImageView().setImage(e.thumbnail());

        current.imageWidthPx = (int) Math.round(e.thumbnail().getWidth());
        current.imageHeightPx = (int) Math.round(e.thumbnail().getHeight());
        viewer.refresh();
    }

    private void doSave() {
        if (current == null) return;
        current.id = templateIdField.getText().trim();
        current.tileWidthPx = tileW.getValue();
        current.tileHeightPx = tileH.getValue();
        if (current.id.isBlank()) return;

        // merge staged edits into the template at save-time
        tilePropsPane.applyEditsTo(current);

        repo.save(current);
        refreshTemplates();
    }

    private void doDelete() {
        if (current == null || current.id == null || current.id.isBlank()) return;
        repo.delete(current.id);
        refreshTemplates();
        newTemplate();
    }

    private void loadTemplate(Path file) {
        current = repo.load(file);
        tilePropsPane.bindTo(current); // bind (and clear staged) only when loading a template
        templateIdField.setText(current.id);
        tileW.getValueFactory().setValue(current.tileWidthPx);
        tileH.getValueFactory().setValue(current.tileHeightPx);

        String abs = manager.toAbsolute(current.logicalPath);
        String url = Path.of(abs).toUri().toString();
        Image img = new Image(url);
        viewer.getImageView().setImage(img);
        viewer.refresh();
    }

    @SuppressWarnings("unchecked")
    private void refreshAssets() {
        AssetScanner scanner = new AssetScanner();
        List<AssetScanner.AssetEntry> entries = scanner.scanAll();

        ObservableList<AssetScanner.AssetEntry> source =
            (ObservableList<AssetScanner.AssetEntry>) filteredAssets.getSource();
        source.setAll(entries);

        filteredAssets.setPredicate(e -> true);
        if (filterField != null) filterField.setText(filterField.getText());
    }

    private void refreshTemplates() {
        templatesList.getItems().setAll(repo.listJsonFiles());
    }

    private static String suggestIdFromPath(String logical) {
        String base = logical.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "_");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
