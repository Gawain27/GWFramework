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
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.Point;
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
    private CheckBox complexBox; // template type

    private FilteredList<AssetScanner.AssetEntry> filteredAssets;
    private ListView<Path> templatesList;

    // NEW: keep a handle to the gallery grid so we can refresh on demand
    private TilePane galleryGrid;

    private TemplateDef current;
    private TilePropertiesPane tilePropsPane;

    @Inject
    private IAssetManager manager;

    @Override
    public void start(Stage stage) {
        Cdi.inject(this);
        repo = new TemplateRepository();

        // LEFT sidebar
        TabPane sidebar = new TabPane();
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebar.getTabs().add(new Tab("Template Gallery", buildTemplateGallery())); // one card per region for complex
        sidebar.getTabs().add(new Tab("Assets", buildAssetsPane()));
        sidebar.getTabs().add(new Tab("Templates", buildTemplatesPane()));

        // Center viewer
        viewer = new GridOverlayPane();
        viewer.setCollisionProvider((gx, gy) -> tilePropsPane == null ? null : tilePropsPane.getEffectiveTile(gx, gy));
        viewer.setRegionsProvider(() -> tilePropsPane == null ? List.of() : tilePropsPane.getEffectiveRegions());
        viewer.setSelectionListener(sel -> {
            if (current == null) return;
            tilePropsPane.showSelection(sel);
            viewer.refresh();
        });
        viewer.setOnKeyPressed(e -> {
            boolean ctrl = e.isControlDown() || e.isMetaDown();
            if (!ctrl) return;
            switch (e.getCode()) {
                case C -> {
                    Point p = viewer.getPrimarySelection();
                    if (p != null) tilePropsPane.copyFrom(p);
                    e.consume();
                }
                case V -> {
                    var sel = viewer.getSelection();
                    if (!sel.isEmpty()) tilePropsPane.pasteTo(sel);
                    viewer.refresh();
                    e.consume();
                }
            }
        });

        BorderPane center = new BorderPane();
        center.setTop(buildTopControls());
        center.setCenter(viewer);

        // Right properties
        tilePropsPane = new TilePropertiesPane();
        tilePropsPane.setMinWidth(300);
        tilePropsPane.setEditsChangedCallback(viewer::refresh);
        tilePropsPane.setSelectionSupplier(viewer::getSelection);

        SplitPane root = new SplitPane(sidebar, center, tilePropsPane);
        root.setOrientation(Orientation.HORIZONTAL);
        root.setDividerPositions(0.25, 0.80);

        Scene scene = new Scene(root, 1080, 640);
        stage.setTitle("GW Template Editor (JavaFX)");
        stage.setScene(scene);
        stage.show();

        // Initial population
        refreshAll();

        // keep assets filter reactive
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

        complexBox = new CheckBox("Complex (has texture regions)");
        VBox complexWrap = new VBox(4, complexBox, new Label("Template Type"));

        Button refreshBtn = new Button("Refresh");     // NEW: refresh all
        refreshBtn.setOnAction(e -> refreshAll());

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

        ToolBar toolbar = new ToolBar(refreshBtn, new Separator(), newBtn, saveBtn, deleteBtn);

        HBox underBar = new HBox(16, idBox, wBox, hBox, complexWrap);
        underBar.setPadding(new Insets(8));
        underBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(toolbar, underBar);
        top.setFillWidth(true);
        return top;
    }

    /* ----------------- Template Gallery (thumbnails) ----------------- */
    private VBox buildTemplateGallery() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        galleryGrid = new TilePane(10, 10);
        galleryGrid.setPrefColumns(3);
        galleryGrid.setPadding(new Insets(4));
        galleryGrid.setPrefTileWidth(260);
        galleryGrid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane scroller = new ScrollPane(galleryGrid);
        scroller.setFitToWidth(true);

        // Render whenever shown
        box.sceneProperty().addListener((o, oldS, newS) -> {
            if (newS != null) renderTemplateCards(galleryGrid);
        });
        box.parentProperty().addListener((o, oldP, newP) -> renderTemplateCards(galleryGrid));

        box.getChildren().addAll(scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        return box;
    }

    /**
     * Renders one card per template (simple) or per region (complex).
     */
    private void renderTemplateCards(TilePane grid) {
        if (grid == null) return;
        grid.getChildren().setAll();

        for (Path p : repo.listJsonFiles()) {
            TemplateDef t = repo.load(p);
            Image tex = null;
            try {
                String abs = manager.toAbsolute(t.logicalPath);
                tex = new Image(Path.of(abs).toUri().toString());
            } catch (Exception ignored) {
            }

            if (t.complex && t.regions != null && !t.regions.isEmpty()) {
                // one card PER REGION
                List<int[]> prs = t.pixelRegions();
                for (int i = 0; i < prs.size(); i++) {
                    int[] r = prs.get(i);
                    String labelId = (t.regions.get(i).id == null || t.regions.get(i).id.isBlank())
                        ? ("region_" + (i + 1)) : t.regions.get(i).id;

                    Image thumb = makeRegionThumbnail(tex, r, 240, 180);

                    VBox card = new VBox(4);
                    card.setPadding(new Insets(6));
                    card.setAlignment(Pos.TOP_CENTER);
                    card.setPrefWidth(260);

                    ImageView iv = new ImageView(thumb);
                    iv.setPreserveRatio(true);
                    iv.setFitWidth(240);
                    iv.setOnMouseClicked(me -> loadTemplate(p));

                    Label name = new Label((t.id == null || t.id.isBlank() ? p.getFileName().toString() : t.id) + " • " + labelId);
                    name.setWrapText(true);
                    name.setMaxWidth(240);

                    Label type = new Label("Region");
                    type.setTextFill(Color.DARKRED);

                    card.getChildren().addAll(iv, name, type);
                    grid.getChildren().add(card);
                }
            } else {
                // single card for simple templates
                Image thumb = makeTemplateThumbnail(t, tex, 240, 180);

                VBox card = new VBox(4);
                card.setPadding(new Insets(6));
                card.setAlignment(Pos.TOP_CENTER);
                card.setPrefWidth(260);

                ImageView iv = new ImageView(thumb);
                iv.setPreserveRatio(true);
                iv.setFitWidth(240);
                iv.setOnMouseClicked(me -> loadTemplate(p));

                Label name = new Label(t.id == null || t.id.isBlank() ? p.getFileName().toString() : t.id);
                name.setWrapText(true);
                name.setMaxWidth(240);

                Label type = new Label("Simple");
                type.setTextFill(Color.DARKGREEN);

                card.getChildren().addAll(iv, name, type);
                grid.getChildren().add(card);
            }
        }
    }

    /**
     * Thumbnail for a whole template (simple or complex as background only).
     */
    private static Image makeTemplateThumbnail(TemplateDef t, Image texture, double maxW, double maxH) {
        double w = maxW, h = maxH;
        if (texture != null) {
            double ratio = texture.getWidth() / texture.getHeight();
            if (w / h > ratio) w = h * ratio;
            else h = w / ratio;
        }
        Canvas c = new Canvas(w, h);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.color(0, 0, 0, 0.05));
        g.fillRect(0, 0, w, h);

        if (texture != null) {
            double imgW = texture.getWidth(), imgH = texture.getHeight();
            double scale = Math.min(w / imgW, h / imgH);
            double dw = imgW * scale, dh = imgH * scale;
            double dx = (w - dw) / 2, dy = (h - dh) / 2;
            g.drawImage(texture, 0, 0, imgW, imgH, dx, dy, dw, dh);

            if (t != null && t.complex && t.regions != null && !t.regions.isEmpty()) {
                g.setStroke(Color.RED);
                g.setLineWidth(2);
                for (int[] pr : t.pixelRegions()) {
                    double rx = dx + pr[0] * scale;
                    double ry = dy + pr[1] * scale;
                    double rw = pr[2] * scale;
                    double rh = pr[3] * scale;
                    g.strokeRect(rx, ry, rw, rh);
                }
            }
        } else {
            g.setFill(Color.GRAY);
            g.fillRect(0, 0, w, h);
            g.setFill(Color.WHITE);
            g.fillText("(no texture)", 10, h / 2);
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    /**
     * Thumbnail for a single region (cropped & scaled).
     */
    private static Image makeRegionThumbnail(Image texture, int[] regionPx, double maxW, double maxH) {
        double w = maxW, h = maxH;
        Canvas c = new Canvas(w, h);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.color(0, 0, 0, 0.05));
        g.fillRect(0, 0, w, h);

        if (texture != null && regionPx != null && regionPx.length == 4) {
            double sx = regionPx[0], sy = regionPx[1], sw = regionPx[2], sh = regionPx[3];
            double scale = Math.min(w / sw, h / sh);
            double dw = sw * scale, dh = sh * scale;
            double dx = (w - dw) / 2, dy = (h - dh) / 2;

            g.drawImage(texture, sx, sy, sw, sh, dx, dy, dw, dh);

            // subtle red frame to indicate it's a region crop
            g.setStroke(Color.RED);
            g.setLineWidth(2);
            g.strokeRect(dx, dy, dw, dh);
        } else {
            g.setFill(Color.GRAY);
            g.fillRect(0, 0, w, h);
            g.setFill(Color.WHITE);
            g.fillText("(no region)", 10, h / 2);
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    /* ----------------- Assets tab ----------------- */
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
            iv.setOnMouseClicked(me -> onAssetSelected(e));

            Label name = new Label(e.logicalPath());
            name.setWrapText(true);
            name.setMaxWidth(240);

            card.getChildren().addAll(iv, name);
            grid.getChildren().add(card);
        }
    }

    /* ----------------- Templates tab (filenames list) ----------------- */
    private VBox buildTemplatesPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        templatesList = new ListView<>();
        templatesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
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
                renderTemplateCards(galleryGrid);
            }
        });
        actions.getChildren().addAll(load, del);

        box.getChildren().addAll(templatesList, actions);
        VBox.setVgrow(templatesList, Priority.ALWAYS);
        return box;
    }

    /* ----------------- Core actions ----------------- */
    private void newTemplate() {
        current = new TemplateDef();
        current.id = "";
        templateIdField.setText("");
        complexBox.setSelected(false);
        tilePropsPane.bindTo(current);
        viewer.getImageView().setImage(null);
        viewer.clearSelection();
        viewer.refresh();
    }

    private void onAssetSelected(AssetScanner.AssetEntry e) {
        if (current == null) {
            current = new TemplateDef();
            tilePropsPane.bindTo(current);
        }
        current.logicalPath = e.logicalPath();
        templateIdField.setText(suggestIdFromPath(e.logicalPath()));

        viewer.getImageView().setImage(e.thumbnail());
        current.imageWidthPx = (int) Math.round(e.thumbnail().getWidth());
        current.imageHeightPx = (int) Math.round(e.thumbnail().getHeight());
        viewer.clearSelection();
        viewer.refresh();
    }

    private void doSave() {
        if (current == null) return;
        current.id = templateIdField.getText().trim();
        current.tileWidthPx = tileW.getValue();
        current.tileHeightPx = tileH.getValue();
        current.complex = complexBox.isSelected();
        if (current.id.isBlank()) return;

        tilePropsPane.applyEditsTo(current);
        repo.save(current);
        refreshTemplates();
        renderTemplateCards(galleryGrid); // refresh gallery thumbnails as well
    }

    private void doDelete() {
        if (current == null || current.id == null || current.id.isBlank()) return;
        repo.delete(current.id);
        refreshTemplates();
        renderTemplateCards(galleryGrid);
        newTemplate();
    }

    private void loadTemplate(Path file) {
        current = repo.load(file);
        tilePropsPane.bindTo(current);
        templateIdField.setText(current.id);
        tileW.getValueFactory().setValue(current.tileWidthPx);
        tileH.getValueFactory().setValue(current.tileHeightPx);
        complexBox.setSelected(current.complex);

        String abs = manager.toAbsolute(current.logicalPath);
        String url = Path.of(abs).toUri().toString();
        Image img = new Image(url);
        viewer.getImageView().setImage(img);
        viewer.clearSelection();
        viewer.refresh();
    }

    /* ----------------- Refresh helpers ----------------- */
    private void refreshAll() {
        refreshAssets();
        refreshTemplates();
        renderTemplateCards(galleryGrid);
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

    /* ----------------- Utils ----------------- */
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
