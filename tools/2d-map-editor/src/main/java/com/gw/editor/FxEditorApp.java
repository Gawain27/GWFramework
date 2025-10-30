package com.gw.editor;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.ui.GridOverlayPane;
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

    @Inject
    private IAssetManager manager;

    @Override
    public void start(Stage stage) {
        Cdi.inject(this);
        repo = new TemplateRepository();

        // Left: tabs (Assets / Templates)
        TabPane sidebar = new TabPane();
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebar.getTabs().add(new Tab("Assets", buildAssetsPane()));
        sidebar.getTabs().add(new Tab("Templates", buildTemplatesPane()));

        // Center: image + grid + controls
        viewer = new GridOverlayPane();

        BorderPane center = new BorderPane(viewer);
        center.setBottom(buildBottomBar());

        SplitPane root = new SplitPane();
        root.getItems().addAll(sidebar, center);
        root.setOrientation(Orientation.HORIZONTAL);

        root.setDividerPositions(0.28);

        // allow grab/resize, set mins so neither collapses
        SplitPane.setResizableWithParent(sidebar, true);
        SplitPane.setResizableWithParent(center,  true);
        sidebar.setMinWidth(220);
        center.setMinWidth(420);

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("GW Template Editor (JavaFX)");
        stage.setScene(scene);
        stage.show();

        // Initial load of assets list
        refreshAssets();
        refreshTemplates();

        filteredAssets.setPredicate(e -> {
            String n = filterField.getText();
            return n == null || n.isBlank() || e.logicalPath().toLowerCase().contains(n.toLowerCase());
        });
    }

    private VBox buildAssetsPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        filterField = new TextField();
        filterField.setPromptText("Filter assets by name…");

        // Grid of thumbnails
        TilePane grid = new TilePane(10, 10);
        grid.setPrefColumns(3);                 // a nice default
        grid.setPadding(new Insets(4));
        grid.setPrefTileWidth(260);             // matches your card width
        grid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);

        // Backing data
        filteredAssets = new FilteredList<>(FXCollections.observableArrayList());
        filterField.textProperty().addListener((obs, o, n) -> {
            Predicate<AssetScanner.AssetEntry> p = e -> n == null || n.isBlank() || e.logicalPath().toLowerCase().contains(n.toLowerCase());
            filteredAssets.setPredicate(p);
            renderAssetTiles(grid);
        });

        // Render initial empty grid
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
            card.getStyleClass().add("asset-card");
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

    private HBox buildBottomBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(8));
        bar.setAlignment(Pos.CENTER_LEFT);

        // Make sure the bar never collapses out of view.
        bar.setMinHeight(48);                 //keep it visible
        bar.setMaxHeight(Region.USE_PREF_SIZE);

        templateIdField = new TextField();
        templateIdField.setPromptText("template id (e.g., crate_small)");

        tileW = new Spinner<>(1, 1024, 16, 1);
        tileH = new Spinner<>(1, 1024, 16, 1);
        viewer.tileWidthProperty().bind(tileW.valueProperty());
        viewer.tileHeightProperty().bind(tileH.valueProperty());

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
            () -> current == null || current.id == null || current.id.isBlank(), templateIdField.textProperty()
        ));
        deleteBtn.setOnAction(e -> doDelete());

        bar.getChildren().addAll(
            new Label("Template Id:"), templateIdField,
            new Separator(Orientation.VERTICAL),
            new Label("Tile W(px):"), tileW,
            new Label("Tile H(px):"), tileH,
            new Separator(Orientation.VERTICAL),
            newBtn, saveBtn, deleteBtn
        );
        return bar;
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
        viewer.getImageView().setImage(null);
    }

    private void onAssetSelected(AssetScanner.AssetEntry e) {
        // Create or update current
        if (current == null) current = new TemplateDef();
        current.logicalPath = e.logicalPath();
        templateIdField.setText(suggestIdFromPath(e.logicalPath()));

        // Show image + set default tile grid
        viewer.getImageView().setImage(e.thumbnail());

        // Cache image px sizes
        current.imageWidthPx = (int)Math.round(e.thumbnail().getWidth());
        current.imageHeightPx = (int)Math.round(e.thumbnail().getHeight());

        // Recompute implicit tiles placeholder if needed (optional for now)
        // We keep tiles empty until we define properties; grid is visual only.
    }

    private void doSave() {
        if (current == null) return;
        current.id = templateIdField.getText().trim();
        current.tileWidthPx = tileW.getValue();
        current.tileHeightPx = tileH.getValue();
        if (current.id.isBlank()) return;
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
        templateIdField.setText(current.id);
        tileW.getValueFactory().setValue(current.tileWidthPx);
        tileH.getValueFactory().setValue(current.tileHeightPx);

        // Resolve to absolute and convert to file: URL
        String abs = manager.toAbsolute(current.logicalPath);
        String url = Path.of(abs).toUri().toString();
        Image img = new Image(url);

        viewer.getImageView().setImage(img);
    }

    @SuppressWarnings("unchecked")
    private void refreshAssets() {
        AssetScanner scanner = new AssetScanner();
        List<AssetScanner.AssetEntry> entries = scanner.scanAll();

        // Update the backing observable list instead of the FilteredList view
        ObservableList<AssetScanner.AssetEntry> source =
            (ObservableList<AssetScanner.AssetEntry>) filteredAssets.getSource();

        source.setAll(entries);

        filteredAssets.setPredicate(e -> true);
        // Trigger UI re-render by re-applying the filter text
        if (filterField != null) filterField.setText(filterField.getText());
    }



    private void refreshTemplates() {
        templatesList.getItems().setAll(repo.listJsonFiles());
    }

    private static String suggestIdFromPath(String logical) {
        String base = logical.replace('\\','/'); int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash+1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "_");
    }

    public static void main(String[] args) { launch(args); }
}
