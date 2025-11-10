package com.gw.editor;

import com.gw.editor.map.MapDef;
import com.gw.editor.map.MapRepository;
import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.ui.GridOverlayPane;
import com.gw.editor.ui.MapCanvasPane;
import com.gw.editor.ui.TemplateInstancePropertiesPane;
import com.gw.editor.ui.TilePropertiesPane;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.util.Cdi;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.Point;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class FxEditorApp extends Application {
    private TemplateRepository repo;
    private MapRepository mapRepo;

    private GridOverlayPane viewer;         // template editor view
    private MapCanvasPane mapView;          // map editor view

    private TextField filterField;
    private Spinner<Integer> tileW;
    private Spinner<Integer> tileH;
    private TextField templateIdField;
    private CheckBox complexBox;
    private CheckBox animatedBox;  // NEW

    private FilteredList<AssetScanner.AssetEntry> filteredAssets;
    private ListView<Path> templatesList;
    private ListView<Path> mapsList;        // map files

    private TilePane galleryGrid;           // template gallery (drag sources)
    private Spinner<Double> galleryScale;   // NEW: scale multiplier for drag

    private TemplateDef current;            // currently edited template
    private TilePropertiesPane tilePropsPane;

    private MapDef currentMap;              // currently edited map
    private TextField mapIdField;
    private Spinner<Integer> mapWSpinner;   // NEW
    private Spinner<Integer> mapHSpinner;   // NEW

    // Map stuff
    private TemplateInstancePropertiesPane instancePropsPane;
    private TabPane centerTabs;
    private SplitPane rootSplit;
    private StackPane rightStack;
    private CheckBox showCollisionsChk;
    private CheckBox showGatesChk;

    // animated gallery support
    private final List<AnimatedCard> animatedCards = new ArrayList<>();
    private AnimationTimer galleryTimer;

    // layers UI
    private ListView<Integer> layerList;
    private Button btnAddLayer;
    private Button btnRemoveLayer;


    @Inject
    private IAssetManager manager;

    @Override
    public void start(Stage stage) {
        Cdi.inject(this);
        repo = new TemplateRepository();
        mapRepo = new MapRepository();

        TabPane sidebar = new TabPane();
        sidebar.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sidebar.getTabs().add(new Tab("Template Gallery", buildTemplateGallery()));
        sidebar.getTabs().add(new Tab("Assets", buildAssetsPane()));
        sidebar.getTabs().add(new Tab("Templates", buildTemplatesPane()));
        sidebar.getTabs().add(new Tab("Maps", buildMapsPane()));

        centerTabs = new TabPane();
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        centerTabs.getTabs().add(new Tab("Template Editor", buildTemplateEditorCenter()));
        centerTabs.getTabs().add(new Tab("Map Editor", buildMapEditorCenter()));
        centerTabs.getSelectionModel().selectedIndexProperty().addListener((o, oldIdx, newIdx) -> {
            showRightPane(newIdx.intValue());
        });

        tilePropsPane = new TilePropertiesPane();
        tilePropsPane.setMinWidth(300);
        tilePropsPane.setEditsChangedCallback(() -> {
            if (viewer != null) viewer.refresh();
        });
        tilePropsPane.setSelectionSupplier(() -> viewer == null ? Set.of() : viewer.getSelection());
        // let TilePropertiesPane disable regions UI when complex is off
        tilePropsPane.bindComplexProperty(complexBox.selectedProperty());

        instancePropsPane = new TemplateInstancePropertiesPane(repo);
        instancePropsPane.setMinWidth(300);

        rightStack = new StackPane(tilePropsPane, instancePropsPane);
        showRightPane(0);

        SplitPane sidebarAndCenters = new SplitPane(sidebar, centerTabs);
        sidebarAndCenters.setDividerPositions(0.25);

        rootSplit = new SplitPane(sidebarAndCenters, rightStack);
        rootSplit.setDividerPositions(0.75);

        Scene scene = new Scene(rootSplit, 1080, 640);
        stage.setTitle("GW Template & Map Editor (JavaFX)");
        stage.setScene(scene);
        stage.show();

        startGalleryTimer();
        refreshAll();

        filteredAssets.setPredicate(e -> {
            String n = filterField.getText();
            return n == null || n.isBlank() || e.logicalPath().toLowerCase().contains(n.toLowerCase());
        });
    }

    /* ==================== TEMPLATE EDITOR ==================== */

    private BorderPane buildTemplateEditorCenter() {
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
        center.setTop(buildTemplateTopControls());
        center.setCenter(viewer);
        return center;
    }

    private VBox buildTemplateTopControls() {
        templateIdField = new TextField();
        templateIdField.setPromptText("crate_small");
        VBox idBox = new VBox(4, templateIdField, new Label("Template Id"));

        tileW = new Spinner<>(1, 1024, 16, 1);
        tileH = new Spinner<>(1, 1024, 16, 1);
        viewer.tileWidthProperty().bind(tileW.valueProperty());
        viewer.tileHeightProperty().bind(tileH.valueProperty());
        VBox wBox = new VBox(4, tileW, new Label("Tile W (px)"));
        VBox hBox = new VBox(4, tileH, new Label("Tile H (px)"));

        complexBox = new CheckBox("Complex (has texture regions)");
        animatedBox = new CheckBox("Animated");
        if (viewer != null)
            viewer.bintAnimated(this.animatedBox.selectedProperty());
        animatedBox.disableProperty().bind(complexBox.selectedProperty().not());
        VBox complexWrap = new VBox(4, complexBox, animatedBox, new Label("Template Type"));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshAll());
        Button newBtn = new Button("New");
        newBtn.setOnAction(e -> newTemplate());
        Button saveBtn = new Button("Save");
        saveBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> current == null || templateIdField.getText().isBlank(), templateIdField.textProperty()));
        saveBtn.setOnAction(e -> doSaveTemplate());
        Button deleteBtn = new Button("Delete");
        deleteBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> current == null || current.id == null || current.id.isBlank(), templateIdField.textProperty()));
        deleteBtn.setOnAction(e -> doDeleteTemplate());

        ToolBar toolbar = new ToolBar(refreshBtn, new Separator(), newBtn, saveBtn, deleteBtn);
        HBox underBar = new HBox(16, idBox, wBox, hBox, complexWrap);
        underBar.setPadding(new Insets(8));
        underBar.setAlignment(Pos.CENTER_LEFT);

        return new VBox(toolbar, underBar);
    }

    private void showRightPane(int centerTabIndex) {
        boolean templateMode = (centerTabIndex == 0);
        tilePropsPane.setVisible(templateMode);
        tilePropsPane.setManaged(templateMode);
        instancePropsPane.setVisible(!templateMode);
        instancePropsPane.setManaged(!templateMode);
    }

    /* ==================== MAP EDITOR ==================== */

    // Map editor center builder: after mapView creation/binding, wire callbacks
    private BorderPane buildMapEditorCenter() {
        mapView = new MapCanvasPane(repo, manager);
        mapView.tileWidthProperty().bind(tileW.valueProperty());
        mapView.tileHeightProperty().bind(tileH.valueProperty());
        mapView.setOnSelectionChanged(selPlacement -> instancePropsPane.refresh(selPlacement));

        BorderPane pane = new BorderPane();
        pane.setTop(buildMapTopControls());
        ScrollPane scroll = new ScrollPane(mapView);
        scroll.setPannable(true);
        pane.setCenter(scroll);
        return pane;
    }

    private VBox buildMapTopControls() {
        mapIdField = new TextField();
        mapIdField.setPromptText("level_1");
        VBox idBox = new VBox(4, mapIdField, new Label("Map Id"));

        mapWSpinner = new Spinner<>(1, 10_000, 64, 1);
        mapHSpinner = new Spinner<>(1, 10_000, 36, 1);
        VBox mwBox = new VBox(4, mapWSpinner, new Label("Map Width (tiles)"));
        VBox mhBox = new VBox(4, mapHSpinner, new Label("Map Height (tiles)"));

        Button newBtn = new Button("New Map");
        newBtn.setOnAction(e -> newMap());
        Button saveBtn = new Button("Save Map");
        saveBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> currentMap == null || mapIdField.getText().isBlank(), mapIdField.textProperty()));
        saveBtn.setOnAction(e -> doSaveMap());
        Button delBtn = new Button("Delete Map");
        delBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> currentMap == null || currentMap.id == null || currentMap.id.isBlank(), mapIdField.textProperty()));
        delBtn.setOnAction(e -> doDeleteMap());

        Button applySize = new Button("Resize Map");
        applySize.setOnAction(e -> {
            if (currentMap == null) return;
            int w = mapWSpinner.getValue(), h = mapHSpinner.getValue();
            if (mapView.setMapSize(w, h)) {
                currentMap.widthTiles = w;
                currentMap.heightTiles = h;
            }
        });

        Button expand = new Button("Expand +10");
        expand.setOnAction(e -> {
            if (currentMap == null) return;
            int w = currentMap.widthTiles + 10, h = currentMap.heightTiles + 10;
            if (mapView.setMapSize(w, h)) {
                currentMap.widthTiles = w;
                currentMap.heightTiles = h;
                mapWSpinner.getValueFactory().setValue(w);
                mapHSpinner.getValueFactory().setValue(h);
            }
        });

        Button shrink = new Button("Shrink -10");
        shrink.setOnAction(e -> {
            if (currentMap == null) return;
            int w = Math.max(1, currentMap.widthTiles - 10);
            int h = Math.max(1, currentMap.heightTiles - 10);
            if (mapView.setMapSize(w, h)) {
                currentMap.widthTiles = w;
                currentMap.heightTiles = h;
                mapWSpinner.getValueFactory().setValue(w);
                mapHSpinner.getValueFactory().setValue(h);
            }
        });

        Button zoomIn = new Button("Zoom +");
        Button zoomOut = new Button("Zoom −");
        Button zoomRst = new Button("Reset");
        zoomIn.setOnAction(e -> mapView.zoomIn());
        zoomOut.setOnAction(e -> mapView.zoomOut());
        zoomRst.setOnAction(e -> mapView.zoomReset());

        var showCollisionsChk = new CheckBox("Show Collisions");
        showCollisionsChk.setSelected(true);
        var showGatesChk = new CheckBox("Show Gates");
        showGatesChk.setSelected(true);
        showCollisionsChk.selectedProperty().addListener((o, ov, nv) -> {
            mapView.showCollisionsProperty().set(nv);
            mapView.requestLayout();
        });
        showGatesChk.selectedProperty().addListener((o, ov, nv) -> {
            mapView.showGatesProperty().set(nv);
            mapView.requestLayout();
        });

        Button newFromAsset = new Button("New Map from Asset…");
        Button pasteAsset = new Button("Paste Asset…");
        newFromAsset.setOnAction(e -> newMapFromAsset());
        pasteAsset.setOnAction(e -> pasteAssetIntoMap());

        layerList = new ListView<>();
        layerList.setPrefHeight(120);
        layerList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : ("Layer " + item));
            }
        });
        layerList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv == null || currentMap == null) return;
            int idx = currentMap.layers.indexOf(nv);
            if (idx >= 0) mapView.currentLayerProperty().set(idx);
        });

        btnAddLayer = new Button("+");
        btnRemoveLayer = new Button("−");
        btnAddLayer.setOnAction(e -> {
            if (currentMap == null) return;
            if (!confirm("Add Layer", "Add a new layer at bottom?")) return;
            int idx = currentMap.addLayer();
            refreshLayerList();
            layerList.getSelectionModel().select(idx);
        });
        btnRemoveLayer.setOnAction(e -> {
            if (currentMap == null) return;
            Integer sel = layerList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int removeIdx = currentMap.layers.indexOf(sel);
            if (removeIdx < 0) return;
            if (!confirm("Remove Layer", "Remove layer " + sel + " and delete everything on it?")) return;
            currentMap.removeLayer(removeIdx);
            refreshLayerList();
            int newSel = Math.min(removeIdx, currentMap.layers.size() - 1);
            if (newSel >= 0) layerList.getSelectionModel().select(newSel);
            mapView.requestLayout();
            instancePropsPane.refresh(mapView.getSelected());
        });

        // NEW: collapsible layer UI with an "eye" toggle
        HBox layerButtons = new HBox(6, btnAddLayer, btnRemoveLayer);
        VBox layerInner = new VBox(6, new Label("Layers"), layerList, layerButtons);
        layerInner.setPadding(new Insets(4));

        ToggleButton eyeToggle = new ToggleButton("👁"); // eye
        eyeToggle.setSelected(true);
        eyeToggle.setOnAction(e -> {
            boolean show = eyeToggle.isSelected();
            layerInner.setVisible(show);
            layerInner.setManaged(show);
        });

        HBox layerHeader = new HBox(8, eyeToggle);
        VBox layerBox = new VBox(6, layerHeader, layerInner);

        // tool bar remains the same (zoom, overlays, etc.)
        ToolBar tb = new ToolBar(new Button("New Map") {{
            setOnAction(e -> newMap());
        }}, new Button("Save Map") {{
            disableProperty().bind(Bindings.createBooleanBinding(() -> currentMap == null || mapIdField.getText().isBlank(), mapIdField.textProperty()));
            setOnAction(e -> doSaveMap());
        }}, new Button("Delete Map") {{
            disableProperty().bind(Bindings.createBooleanBinding(() -> currentMap == null || currentMap.id == null || currentMap.id.isBlank(), mapIdField.textProperty()));
            setOnAction(e -> doDeleteMap());
        }}, new Separator(), new Button("Resize Map") {{
            setOnAction(e -> {
                if (currentMap == null) return;
                int w = mapWSpinner.getValue(), h = mapHSpinner.getValue();
                if (mapView.setMapSize(w, h)) {
                    currentMap.widthTiles = w;
                    currentMap.heightTiles = h;
                }
            });
        }}, new Button("Expand +10") {{
            setOnAction(e -> {
                if (currentMap == null) return;
                int w = currentMap.widthTiles + 10, h = currentMap.heightTiles + 10;
                if (mapView.setMapSize(w, h)) {
                    currentMap.widthTiles = w;
                    currentMap.heightTiles = h;
                    mapWSpinner.getValueFactory().setValue(w);
                    mapHSpinner.getValueFactory().setValue(h);
                }
            });
        }}, new Button("Shrink -10") {{
            setOnAction(e -> {
                if (currentMap == null) return;
                int w = Math.max(1, currentMap.widthTiles - 10);
                int h = Math.max(1, currentMap.heightTiles - 10);
                if (mapView.setMapSize(w, h)) {
                    currentMap.widthTiles = w;
                    currentMap.heightTiles = h;
                    mapWSpinner.getValueFactory().setValue(w);
                    mapHSpinner.getValueFactory().setValue(h);
                }
            });
        }}, new Separator(), new Button("Zoom +") {{
            setOnAction(e -> mapView.zoomIn());
        }}, new Button("Zoom −") {{
            setOnAction(e -> mapView.zoomOut());
        }}, new Button("Reset") {{
            setOnAction(e -> mapView.zoomReset());
        }}, new Separator(), new CheckBox("Show Collisions") {{
            setSelected(true);
            selectedProperty().addListener((o, ov, nv) -> {
                mapView.showCollisionsProperty().set(nv);
                mapView.requestLayout();
            });
        }}, new CheckBox("Show Gates") {{
            setSelected(true);
            selectedProperty().addListener((o, ov, nv) -> {
                mapView.showGatesProperty().set(nv);
                mapView.requestLayout();
            });
        }}, new Separator(), new Button("New Map from Asset…") {{
            setOnAction(e -> newMapFromAsset());
        }}, new Button("Paste Asset…") {{
            setOnAction(e -> pasteAssetIntoMap());
        }});

        // id / size rows unchanged

        HBox under = new HBox(24, new VBox(4, idBox), new VBox(4, mwBox), new VBox(4, mhBox), layerBox);
        under.setPadding(new Insets(8));
        under.setAlignment(Pos.CENTER_LEFT);

        return new VBox(tb, under);
    }

    private void newMapFromAsset() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Map from Asset");
        dlg.setHeaderText(null);
        dlg.setContentText("Enter logical path of asset (e.g. textures/level_bg.png):");
        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        String logical = res.get().trim();
        try {
            String abs = manager.toAbsolute(logical);
            var img = new Image(Path.of(abs).toUri().toString());
            if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) {
                alert("Could not load image for asset: " + logical);
                return;
            }
            // Confirm creation
            if (!confirm("Create Map", "Create a new map sized to the asset as background?")) return;

            // Compute tile grid from current tileW/H
            int tW = tileW.getValue(), tH = tileH.getValue();
            int wTiles = Math.max(1, (int) Math.ceil(img.getWidth() / tW));
            int hTiles = Math.max(1, (int) Math.ceil(img.getHeight() / tH));

            currentMap = new MapDef();
            currentMap.id = "";
            mapIdField.setText("");

            currentMap.tileWidthPx = tW;
            currentMap.tileHeightPx = tH;
            currentMap.widthTiles = wTiles;
            currentMap.heightTiles = hTiles;
            currentMap.normalizeLayers();

            currentMap.background = new MapDef.Background();
            currentMap.background.logicalPath = logical;
            currentMap.background.imageWidthPx = (int) Math.round(img.getWidth());
            currentMap.background.imageHeightPx = (int) Math.round(img.getHeight());

            mapView.bindMap(currentMap);
            instancePropsPane.bindMap(currentMap);
            refreshLayerList();
            if (!currentMap.layers.isEmpty()) layerList.getSelectionModel().select(0);

        } catch (Exception ex) {
            alert("Error: " + ex.getMessage());
        }
    }

    private void pasteAssetIntoMap() {
        if (currentMap == null) {
            alert("Open or create a map first.");
            return;
        }

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Paste Asset into Map");
        dlg.setHeaderText(null);
        dlg.setContentText("Enter logical path of asset (e.g. textures/tree.png):");
        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        String logical = res.get().trim();
        try {
            String abs = manager.toAbsolute(logical);
            var img = new Image(Path.of(abs).toUri().toString());
            if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) {
                alert("Could not load image for asset: " + logical);
                return;
            }

            int tW = tileW.getValue(), tH = tileH.getValue();
            int wTiles = Math.max(1, (int) Math.ceil(img.getWidth() / tW));
            int hTiles = Math.max(1, (int) Math.ceil(img.getHeight() / tH));

            // Offer autoresize if it doesn't fit
            boolean needsResize = (wTiles > currentMap.widthTiles) || (hTiles > currentMap.heightTiles);
            if (needsResize) {
                if (!confirm("Auto-resize Map", "Asset footprint is larger than the current map (" + wTiles + "×" + hTiles + " tiles). Resize the map to fit?"))
                    return;
                currentMap.widthTiles = Math.max(currentMap.widthTiles, wTiles);
                currentMap.heightTiles = Math.max(currentMap.heightTiles, hTiles);
                mapWSpinner.getValueFactory().setValue(currentMap.widthTiles);
                mapHSpinner.getValueFactory().setValue(currentMap.heightTiles);
                mapView.setMapSize(currentMap.widthTiles, currentMap.heightTiles);
            }

            // Create a transient TemplateDef snapshot for this asset
            TemplateDef snap = new TemplateDef();
            snap.id = "(asset:" + logical + ")";
            snap.logicalPath = logical;
            snap.imageWidthPx = (int) Math.round(img.getWidth());
            snap.imageHeightPx = (int) Math.round(img.getHeight());
            snap.tileWidthPx = tW;
            snap.tileHeightPx = tH;
            snap.complex = false;

            MapDef.Placement p = new MapDef.Placement(snap.id, -1, 0, 0, wTiles, hTiles, 0, 0, snap.imageWidthPx, snap.imageHeightPx, snap, Math.max(0, Math.min(mapView.currentLayerProperty().get(), currentMap.layers.size() - 1)), galleryScale.getValue());

            currentMap.placements.add(p);
            mapView.selectPid(p.pid);
            mapView.requestLayout();

        } catch (Exception ex) {
            alert("Error: " + ex.getMessage());
        }
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /* ==================== LEFT: Template Gallery (drag source) ==================== */

    private VBox buildTemplateGallery() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        // top bar: Scale spinner (multiplier in tiles)
        galleryScale = new Spinner<>(0.25, 8.0, 1.0, 0.25);
        galleryScale.setEditable(true);
        ToolBar top = new ToolBar(new Label("Scale:"), galleryScale);

        galleryGrid = new TilePane(10, 10);
        galleryGrid.setPrefColumns(3);
        galleryGrid.setPadding(new Insets(4));
        galleryGrid.setPrefTileWidth(260);
        galleryGrid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane scroller = new ScrollPane(galleryGrid);
        scroller.setFitToWidth(true);

        box.sceneProperty().addListener((o, oldS, newS) -> {
            if (newS != null) renderTemplateCards(galleryGrid);
        });
        box.parentProperty().addListener((o, oldP, newP) -> renderTemplateCards(galleryGrid));

        box.getChildren().addAll(top, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        return box;
    }

    private void renderTemplateCards(TilePane grid) {
        if (grid == null) return;
        grid.getChildren().setAll();
        animatedCards.clear(); // rebuild animated list

        for (Path p : repo.listJsonFiles()) {
            TemplateDef t = repo.load(p);
            Image tex = null;
            try {
                String abs = manager.toAbsolute(t.logicalPath);
                tex = new Image(Path.of(abs).toUri().toString());
            } catch (Exception ignored) {
            }

            if (t.complex && t.animated && t.regions != null && !t.regions.isEmpty()) {
                // ONE draggable card for the animation. Use first frame for thumbnail & preview sizing.
                var frames = t.pixelRegions();
                int[] first = frames.getFirst();
                Image thumb = makeRegionThumb(tex, first, 240, 180, t.animated);

                String title = (t.id == null || t.id.isBlank() ? p.getFileName().toString() : t.id) + " • (animated)";
                var card = cardForTemplate(t, p, thumb, title, "Animated");

                // regionIndex = -1 means "use whole template snapshot" (keeps all frames)
                // last field is scale multiplier, default 1.0
                card.imageView.setOnDragDetected(e -> {
                    var db = card.imageView.startDragAndDrop(TransferMode.COPY);
                    db.setDragView(card.imageView.getImage(),
                        card.imageView.getImage().getWidth()/2,
                        card.imageView.getImage().getHeight()/2);

                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    // AFTER: use the gallery scale spinner value
                    double scaleMul = (galleryScale.getValue() == null ? 1.0 : galleryScale.getValue());
                    String payload = String.join("|",
                        safe(t.id),
                        String.valueOf(-1),                                   // regionIndex (animated whole)
                        String.valueOf(Math.max(1, t.tileWidthPx)),
                        String.valueOf(Math.max(1, t.tileHeightPx)),
                        String.valueOf(first[0]), String.valueOf(first[1]),   // preview rect = first frame
                        String.valueOf(first[2]), String.valueOf(first[3]),
                        String.valueOf(scaleMul)                               // <-- pass real scale
                    );
                    cc.put(MapCanvasPane.DND_FORMAT, payload);
                    db.setContent(cc);
                    e.consume();
                });

                grid.getChildren().add(card.root);

            } else if (t.complex && t.regions != null && !t.regions.isEmpty()) {
                // non-animated complex → one card per region
                var prs = t.pixelRegions();
                for (int i = 0; i < prs.size(); i++) {
                    int[] r = prs.get(i);
                    String rid = (t.regions.get(i).id == null || t.regions.get(i).id.isBlank()) ? ("region_" + (i + 1)) : t.regions.get(i).id;

                    Image thumb = makeRegionThumb(tex, r, 240, 180, /*blue*/false);
                    var card = cardForTemplate(t, p, thumb, t.id + " • " + rid, "Region");
                    enableDrag(card.imageView, t, i, r);
                    grid.getChildren().add(card.root);
                }
            } else {
                Image thumb = makeTemplateThumb(t, tex, 240, 180);
                var card = cardForTemplate(t, p, thumb, t.id == null || t.id.isBlank() ? p.getFileName().toString() : t.id, "Simple");
                int[] full = new int[]{0, 0, t.imageWidthPx, t.imageHeightPx};
                enableDrag(card.imageView, t, -1, full);
                grid.getChildren().add(card.root);
            }
        }
    }

    private record Card(VBox root, ImageView imageView) {
    }

    private Card cardForTemplate(TemplateDef t, Path file, Image img, String title, String type) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(6));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(260);

        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(240);
        iv.setOnMouseClicked(me -> loadTemplate(file));

        Label name = new Label(title);
        name.setWrapText(true);
        name.setMaxWidth(240);

        Label subt = new Label(type);
        subt.setTextFill("Animated".equals(type) ? Color.DARKBLUE : "Region".equals(type) ? Color.DARKRED : Color.DARKGREEN);

        card.getChildren().addAll(iv, name, subt);
        return new Card(card, iv);
    }

    /**
     * Make the image view a drag source with our custom payload.
     */
    private void enableDrag(ImageView view, TemplateDef t, int regionIndex, int[] rPx) {
        view.setOnDragDetected(e -> {
            var db = view.startDragAndDrop(TransferMode.COPY);
            db.setDragView(view.getImage(), view.getImage().getWidth() / 2, view.getImage().getHeight() / 2);

            double scaleMul = galleryScale.getValue() == null ? 1.0 : galleryScale.getValue();

            ClipboardContent cc = new ClipboardContent();
            String payload = String.join("|", safe(t.id), String.valueOf(regionIndex), // -2 = animated whole (use regions)
                String.valueOf(Math.max(1, t.tileWidthPx)), String.valueOf(Math.max(1, t.tileHeightPx)), String.valueOf(rPx[0]), String.valueOf(rPx[1]), String.valueOf(rPx[2]), String.valueOf(rPx[3]), String.valueOf(scaleMul));
            cc.put(MapCanvasPane.DND_FORMAT, payload);
            db.setContent(cc);
            e.consume();
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /* ==================== LEFT: Assets / Templates / Maps lists ==================== */

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

    private VBox buildMapsPane() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        mapsList = new ListView<>();
        mapsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
        mapsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Path p = mapsList.getSelectionModel().getSelectedItem();
                if (p != null) loadMap(p);
            }
        });

        HBox actions = new HBox(8);
        Button load = new Button("Load");
        load.setOnAction(e -> {
            Path p = mapsList.getSelectionModel().getSelectedItem();
            if (p != null) loadMap(p);
        });
        Button del = new Button("Delete");
        del.setOnAction(e -> {
            Path p = mapsList.getSelectionModel().getSelectedItem();
            if (p != null) {
                String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                mapRepo.delete(id);
                refreshMaps();
            }
        });

        actions.getChildren().addAll(load, del);
        box.getChildren().addAll(mapsList, actions);
        VBox.setVgrow(mapsList, Priority.ALWAYS);
        return box;
    }

    /* ==================== Template flows ==================== */

    private void newTemplate() {
        current = new TemplateDef();
        current.id = "";
        templateIdField.setText("");
        complexBox.setSelected(false);
        animatedBox.setSelected(false);
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

    private void doSaveTemplate() {
        if (current == null) return;
        current.id = templateIdField.getText().trim();
        current.tileWidthPx = tileW.getValue();
        current.tileHeightPx = tileH.getValue();
        current.complex = complexBox.isSelected();
        current.animated = animatedBox.isSelected();
        if (current.id.isBlank()) return;
        tilePropsPane.applyEditsTo(current);
        repo.save(current);
        refreshTemplates();
        renderTemplateCards(galleryGrid);
    }

    private void doDeleteTemplate() {
        if (current == null || current.id == null || current.id.isBlank()) return;
        if (!confirm("Delete Template", "Delete template '" + current.id + "'?")) return;
        repo.delete(current.id);
        refreshTemplates();
        renderTemplateCards(galleryGrid);
        newTemplate();
    }

    private boolean confirm(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(title);
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void loadTemplate(Path file) {
        if (current != null && !templateIdField.getText().isBlank()) {
            if (!confirm("Load Template", "Discard unsaved changes and load selected template?")) return;
        }
        current = repo.load(file);
        tilePropsPane.bindTo(current);
        templateIdField.setText(current.id);
        tileW.getValueFactory().setValue(current.tileWidthPx);
        tileH.getValueFactory().setValue(current.tileHeightPx);
        complexBox.setSelected(current.complex);
        animatedBox.setSelected(current.animated);

        String abs = manager.toAbsolute(current.logicalPath);
        String url = Path.of(abs).toUri().toString();
        Image img = new Image(url);
        viewer.getImageView().setImage(img);
        viewer.clearSelection();
        viewer.refresh();
    }

    /* ==================== Map flows ==================== */
    // When map changes (new/load), rebind instance pane to map:
    private void newMap() {
        currentMap = new MapDef();
        currentMap.id = "";
        mapIdField.setText("");
        currentMap.tileWidthPx = tileW.getValue();
        currentMap.tileHeightPx = tileH.getValue();
        currentMap.widthTiles = mapWSpinner.getValue();
        currentMap.heightTiles = mapHSpinner.getValue();
        currentMap.normalizeLayers();
        mapView.bindMap(currentMap);
        instancePropsPane.bindMap(currentMap);
        refreshLayerList();
        if (!currentMap.layers.isEmpty()) layerList.getSelectionModel().select(0);
    }

    private void doSaveMap() {
        if (currentMap == null) return;
        currentMap.id = mapIdField.getText().trim();
        currentMap.tileWidthPx = tileW.getValue();
        currentMap.tileHeightPx = tileH.getValue();
        if (currentMap.id.isBlank()) return;
        mapRepo.save(currentMap);
        refreshMaps();
    }

    private void doDeleteMap() {
        if (currentMap == null || currentMap.id == null || currentMap.id.isBlank()) return;
        if (!confirm("Delete Map", "Delete map '" + currentMap.id + "'?")) return;
        mapRepo.delete(currentMap.id);
        refreshMaps();
        newMap();
    }

    private void loadMap(Path file) {
        if (currentMap != null && !mapIdField.getText().isBlank()) {
            if (!confirm("Load Map", "Discard unsaved changes and load selected map?")) return;
        }
        currentMap = mapRepo.load(file);
        currentMap.normalizeLayers();
        mapIdField.setText(currentMap.id);
        tileW.getValueFactory().setValue(currentMap.tileWidthPx);
        tileH.getValueFactory().setValue(currentMap.tileHeightPx);
        mapWSpinner.getValueFactory().setValue(currentMap.widthTiles);
        mapHSpinner.getValueFactory().setValue(currentMap.heightTiles);
        mapView.bindMap(currentMap);
        instancePropsPane.bindMap(currentMap);
        refreshLayerList();
        if (!currentMap.layers.isEmpty())
            layerList.getSelectionModel().select(Math.min(0, currentMap.layers.size() - 1));
    }

    /* ==================== Refresh helpers & thumbnails ==================== */

    private void refreshAll() {
        refreshAssets();
        refreshTemplates();
        refreshMaps();
        refreshLayerList();
        renderTemplateCards(galleryGrid);
    }

    private void refreshLayerList() {
        if (layerList == null) return;
        layerList.getItems().setAll(currentMap == null ? List.of() : currentMap.layers);
    }

    @SuppressWarnings("unchecked")
    private void refreshAssets() {
        AssetScanner scanner = new AssetScanner();
        List<AssetScanner.AssetEntry> entries = scanner.scanAll();

        ObservableList<AssetScanner.AssetEntry> source = (ObservableList<AssetScanner.AssetEntry>) filteredAssets.getSource();
        source.setAll(entries);

        filteredAssets.setPredicate(e -> true);
        if (filterField != null) filterField.setText(filterField.getText());
    }

    private void refreshTemplates() {
        templatesList.getItems().setAll(repo.listJsonFiles());
    }

    private void refreshMaps() {
        mapsList.getItems().setAll(mapRepo.listJsonFiles());
    }

    private static Image makeTemplateThumb(TemplateDef t, Image texture, double maxW, double maxH) {
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
            if (t != null && t.regions != null && !t.regions.isEmpty() && t.complex) {
                g.setStroke(t.animated ? Color.BLUE : Color.RED);
                g.setLineWidth(2);
                for (int[] pr : t.pixelRegions()) {
                    double rx = dx + pr[0] * scale, ry = dy + pr[1] * scale;
                    double rw = pr[2] * scale, rh = pr[3] * scale;
                    g.strokeRect(rx, ry, rw, rh);
                }
            }
        }
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    private static Image makeRegionThumb(Image texture, int[] r, double maxW, double maxH, boolean animated) {
        double w = maxW, h = maxH;
        Canvas c = new Canvas(w, h);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.color(0, 0, 0, 0.05));
        g.fillRect(0, 0, w, h);
        if (texture != null) {
            double sx = r[0], sy = r[1], sw = r[2], sh = r[3];
            double scale = Math.min(w / sw, h / sh);
            double dw = sw * scale, dh = sh * scale;
            double dx = (w - dw) / 2, dy = (h - dh) / 2;
            g.drawImage(texture, sx, sy, sw, sh, dx, dy, dw, dh);
            g.setStroke(animated ? Color.BLUE : Color.RED);
            g.setLineWidth(2);
            g.strokeRect(dx, dy, dw, dh);
        }
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return c.snapshot(sp, null);
    }

    private static String suggestIdFromPath(String logical) {
        String base = logical.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "_");
    }

    /* ==== gallery animation driver ==== */
    private void startGalleryTimer() {
        if (galleryTimer != null) return;
        galleryTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                for (AnimatedCard ac : animatedCards) ac.tick();
            }
        };
        galleryTimer.start();
    }

    private static class AnimatedCard {
        private final ImageView iv;
        private final Image texture;
        private final List<int[]> frames;
        private final double maxW, maxH;
        private int lastDrawnSlot = -1;
        private final DoubleProperty fpsSlots = new SimpleDoubleProperty(60); // 60-slot timeline

        AnimatedCard(ImageView iv, Image texture, List<int[]> frames, double maxW, double maxH) {
            this.iv = iv;
            this.texture = texture;
            this.frames = frames;
            this.maxW = maxW;
            this.maxH = maxH;
        }

        void tick() {
            if (texture == null || frames == null || frames.isEmpty()) return;
            long now = System.nanoTime();
            int slot = (int) ((now / (1_000_000_000L / 60)) % 60);
            if (slot == lastDrawnSlot) return;
            lastDrawnSlot = slot;

            int idx = frames.size() >= 60 ? (int) Math.floor(slot * (frames.size() / 60.0)) : (int) (slot % frames.size());

            int[] r = frames.get(Math.max(0, Math.min(idx, frames.size() - 1)));
            Image thumb = makeRegionThumb(texture, r, maxW, maxH, true);
            iv.setImage(thumb);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
