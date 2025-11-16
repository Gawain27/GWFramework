package com.gw.world.ui;

import com.gw.editor.template.TemplateRepository;
import com.gw.map.io.MapRepository;
import com.gw.map.model.MapDef;
import com.gw.world.io.WorldRepository;
import com.gw.world.model.WorldDef;
import com.gw.world.model.WorldDef.SectionPlacement;
import com.gw.world.ui.WorldIsoRenderer.GhostSection;
import com.gw.world.ui.dialog.NewWorldDialog;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class WorldEditorPane extends BorderPane {

    private static final DataFormat MAP_SECTION_FORMAT = new DataFormat("gw.map.section.id");

    private final WorldRepository worldRepo;
    private final MapRepository mapRepo;
    private final TemplateRepository templateRepo;
    // Rendering
    private final Canvas canvas = new Canvas(900, 620);
    private final WorldIsoRenderer renderer = new WorldIsoRenderer(canvas);
    private final AnimationTimer animTimer;
    // Left sidebar - worlds
    private final ListView<WorldEntry> worldList = new ListView<>();
    private final Button btnNewWorld = new Button("New");
    private final Button btnSaveWorld = new Button("Save Current");
    private final Button btnDeleteWorld = new Button("Delete Selected");
    private final Button btnRefreshWorlds = new Button("Refresh Worlds");

    // Left sidebar - maps (sections source)
    private final ListView<MapEntry> mapList = new ListView<>();
    private final Button btnRefreshMaps = new Button("Refresh Maps");
    // Left sidebar - sections in world
    private final ListView<SectionEntry> sectionList = new ListView<>();
    private final Button btnMoveNegX = new Button("X-");
    private final Button btnMovePosX = new Button("X+");
    private final Button btnMoveNegY = new Button("Y-");
    private final Button btnMovePosY = new Button("Y+");
    private final Button btnMoveNegZ = new Button("Z-");
    private final Button btnMovePosZ = new Button("Z+");
    private final Button btnRefreshSections = new Button("Refresh Sections");
    // Near other section controls
    private final Button btnDeleteSection = new Button("Delete Section");

    private WorldDef world = WorldDef.createDefault();
    // Mouse state (camera)
    private double lastX, lastY;
    private boolean rightDragging = false;

    public WorldEditorPane(WorldRepository worldRepo, MapRepository mapRepo, TemplateRepository templateRepo) {
        this.worldRepo = Objects.requireNonNull(worldRepo);
        this.mapRepo = Objects.requireNonNull(mapRepo);
        this.templateRepo = Objects.requireNonNull(templateRepo);

        setPadding(new Insets(8));

        renderer.setWorld(world);
        renderer.setTemplateRepository(this.templateRepo);

        setLeft(buildLeftSidebar());
        setCenter(buildCenterCanvas());

        hookWorldListBehaviour();
        hookMapListBehaviour();
        hookSectionListBehaviour();

        hookCanvasMouse();
        hookCanvasDnD();

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                redraw();
            }
        };
        animTimer.start();

        reloadWorldList();
        reloadMapList();
        reloadSectionList();
    }

    /* ============================================================
     *  Layout
     * ============================================================ */

    private static String stripExt(String s) {
        int idx = s.lastIndexOf('.');
        return (idx >= 0) ? s.substring(0, idx) : s;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private VBox buildLeftSidebar() {
        // ---- worlds ----
        worldList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(WorldEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.name() + "  [" + item.id() + "]");
            }
        });
        worldList.setPrefHeight(160);

        btnNewWorld.setMaxWidth(Double.MAX_VALUE);
        btnSaveWorld.setMaxWidth(Double.MAX_VALUE);
        btnDeleteWorld.setMaxWidth(Double.MAX_VALUE);
        btnRefreshWorlds.setMaxWidth(Double.MAX_VALUE);

        btnNewWorld.setOnAction(e -> doNewWorld());
        btnSaveWorld.setOnAction(e -> doSaveWorld());
        btnDeleteWorld.setOnAction(e -> doDeleteSelectedWorld());
        btnRefreshWorlds.setOnAction(e -> reloadWorldList());

        ToolBar worldBar = new ToolBar(new Label("Worlds"), new Separator(Orientation.VERTICAL), btnNewWorld, btnSaveWorld);
        worldBar.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f8f8, #e8e8e8);");

        VBox worldBox = new VBox(4, worldBar, worldList, new VBox(4, btnDeleteWorld, btnRefreshWorlds));
        worldBox.setPadding(new Insets(4));

        // ---- map list (section source) ----
        mapList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MapEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.name() + "  [" + item.id() + "]");
            }
        });
        mapList.setPrefHeight(160);
        VBox.setVgrow(mapList, Priority.NEVER);

        btnRefreshMaps.setMaxWidth(Double.MAX_VALUE);
        btnRefreshMaps.setOnAction(e -> reloadMapList());

        Label mapsLabel = new Label("Sections (Maps)");
        mapsLabel.setStyle("-fx-font-weight: bold;");

        VBox mapsBox = new VBox(4, mapsLabel, mapList, btnRefreshMaps);
        mapsBox.setPadding(new Insets(4));
        mapsBox.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 1 0 0 0;");

        // ---- sections in current world ----
        sectionList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SectionEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.label());
                }
            }
        });
        sectionList.setPrefHeight(180);
        VBox.setVgrow(sectionList, Priority.ALWAYS);

        styleMoveButton(btnMoveNegX);
        styleMoveButton(btnMovePosX);
        styleMoveButton(btnMoveNegY);
        styleMoveButton(btnMovePosY);
        styleMoveButton(btnMoveNegZ);
        styleMoveButton(btnMovePosZ);

        btnMoveNegX.setOnAction(e -> moveSelectedSection(-1, 0, 0));
        btnMovePosX.setOnAction(e -> moveSelectedSection(+1, 0, 0));
        btnMoveNegY.setOnAction(e -> moveSelectedSection(0, -1, 0));
        btnMovePosY.setOnAction(e -> moveSelectedSection(0, +1, 0));
        btnMoveNegZ.setOnAction(e -> moveSelectedSection(0, 0, -1));
        btnMovePosZ.setOnAction(e -> moveSelectedSection(0, 0, +1));

        btnRefreshSections.setMaxWidth(Double.MAX_VALUE);
        btnRefreshSections.setOnAction(e -> reloadSectionList());

        GridPane moveGrid = new GridPane();
        moveGrid.setHgap(4);
        moveGrid.setVgap(4);

        int r = 0;
        moveGrid.add(new Label("X:"), 0, r);
        moveGrid.add(btnMoveNegX, 1, r);
        moveGrid.add(btnMovePosX, 2, r++);
        moveGrid.add(new Label("Y:"), 0, r);
        moveGrid.add(btnMoveNegY, 1, r);
        moveGrid.add(btnMovePosY, 2, r++);
        moveGrid.add(new Label("Z:"), 0, r);
        moveGrid.add(btnMoveNegZ, 1, r);
        moveGrid.add(btnMovePosZ, 2, r);

        // after moveGrid and before sectionsBox creation

        btnDeleteSection.setMaxWidth(Double.MAX_VALUE);
        btnDeleteSection.setOnAction(e -> deleteSelectedSection());

        HBox sectionsButtons = new HBox(4, btnDeleteSection, btnRefreshSections);

        Label sectionsLabel = new Label("Sections in world");
        sectionsLabel.setStyle("-fx-font-weight: bold;");

        VBox sectionsBox = new VBox(4,
            sectionsLabel,
            sectionList,
            moveGrid,
            sectionsButtons
        );

        VBox left = new VBox(4, worldBox, mapsBox, sectionsBox);
        left.setPadding(new Insets(4));
        left.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #c0c0c0; -fx-border-width: 0 1 0 0;");
        left.setPrefWidth(320);
        return left;
    }

    private void deleteSelectedSection() {
        if (world == null || world.sections == null) return;

        SectionEntry entry = sectionList.getSelectionModel().getSelectedItem();
        if (entry == null) return;

        SectionPlacement sp = entry.section();
        world.sections.remove(sp);

        renderer.setSelectedSection(null);
        reloadSectionList();
        redraw();
    }

    /* ============================================================
     *  World list
     * ============================================================ */

    private void styleMoveButton(Button b) {
        b.setMaxWidth(Double.MAX_VALUE);
    }

    private VBox buildCenterCanvas() {
        VBox center = new VBox(canvas);
        VBox.setVgrow(canvas, Priority.ALWAYS);
        center.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f8f8, #f0f0f0);");
        return center;
    }

    private void hookWorldListBehaviour() {
        worldList.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) loadWorld(newSel);
        });

        worldList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                WorldEntry sel = worldList.getSelectionModel().getSelectedItem();
                if (sel != null) loadWorld(sel);
            }
        });
    }

    private void reloadWorldList() {
        List<Path> files = worldRepo.listJsonFiles();
        List<WorldEntry> entries = files.stream().map(p -> {
            WorldDef def = worldRepo.load(p);
            String id = def.id != null && !def.id.isBlank() ? def.id : stripExt(p.getFileName().toString());
            String nm = def.name != null && !def.name.isBlank() ? def.name : "(unnamed)";
            return new WorldEntry(id, nm, p);
        }).collect(Collectors.toList());

        worldList.getItems().setAll(entries);

        if (world != null && world.id != null && !world.id.isBlank()) {
            for (WorldEntry e : entries) {
                if (e.id().equals(world.id)) {
                    worldList.getSelectionModel().select(e);
                    break;
                }
            }
        }
    }

    private void loadWorld(WorldEntry entry) {
        if (entry == null) return;
        WorldDef def = worldRepo.load(entry.path());
        if (def == null) return;

        this.world = def;
        world.rehydrateSections(this::loadMapById);

        renderer.setWorld(world);
        renderer.setSelectedSection(null);
        reloadSectionList();
        redraw();
    }

    /* ============================================================
     *  Map list (source for sections)
     * ============================================================ */

    private MapDef loadMapById(String id) {
        if (id == null || id.isBlank()) return null;
        return mapRepo.listJsonFiles().stream().filter(p -> p.getFileName().toString().equals(id + ".json")).findFirst().map(mapRepo::load).orElse(null);
    }

    private void hookMapListBehaviour() {
        mapList.setCellFactory(list -> {
            ListCell<MapEntry> cell = new ListCell<>() {
                @Override
                protected void updateItem(MapEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setText(null);
                    else setText(item.name() + "  [" + item.id() + "]");
                }
            };

            cell.setOnDragDetected(e -> {
                MapEntry item = cell.getItem();
                if (item == null) return;

                var db = cell.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.put(MAP_SECTION_FORMAT, item.id());
                db.setContent(content);
                e.consume();
            });

            return cell;
        });
    }

    /* ============================================================
     *  Sections in world
     * ============================================================ */

    private void reloadMapList() {
        List<Path> files = mapRepo.listJsonFiles();
        List<MapEntry> entries = files.stream().map(p -> {
            MapDef def = mapRepo.load(p);
            String id = def.id != null && !def.id.isBlank() ? def.id : stripExt(p.getFileName().toString());
            String nm = def.name != null && !def.name.isBlank() ? def.name : "(unnamed)";
            return new MapEntry(id, nm, p);
        }).collect(Collectors.toList());
        mapList.getItems().setAll(entries);
    }

    private void hookSectionListBehaviour() {
        sectionList.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                renderer.setSelectedSection(newSel.section());
            } else {
                renderer.setSelectedSection(null);
            }
            redraw();
        });
    }

    private void reloadSectionList() {
        sectionList.getItems().clear();
        if (world == null || world.sections == null || world.sections.isEmpty()) return;

        SectionPlacement currentSelected = null;
        SectionEntry existingSelection = sectionList.getSelectionModel().getSelectedItem();
        if (existingSelection != null) {
            currentSelected = existingSelection.section();
        }

        int idx = 0;
        for (SectionPlacement sp : world.sections) {
            String mapName = (sp.map != null && sp.map.name != null && !sp.map.name.isBlank()) ? sp.map.name : (sp.mapId != null ? sp.mapId : "(unknown)");
            String label = "#" + idx + " " + mapName + " @ (" + sp.wx + "," + sp.wy + "," + sp.wz + ")";
            sectionList.getItems().add(new SectionEntry(label, sp));
            idx++;
        }

        if (currentSelected != null) {
            for (SectionEntry e : sectionList.getItems()) {
                if (e.section() == currentSelected) {
                    sectionList.getSelectionModel().select(e);
                    break;
                }
            }
        }
    }

    /* ============================================================
     *  Commands: New / Save / Delete
     * ============================================================ */

    private void moveSelectedSection(int dx, int dy, int dz) {
        SectionEntry entry = sectionList.getSelectionModel().getSelectedItem();
        if (entry == null || world == null) return;

        SectionPlacement sp = entry.section();
        sp.wx += dx;
        sp.wy += dy;
        sp.wz += dz;

        world.clampSectionToWorld(sp);

        reloadSectionList();
        // reselect same placement
        for (SectionEntry e : sectionList.getItems()) {
            if (e.section() == sp) {
                sectionList.getSelectionModel().select(e);
                break;
            }
        }
        renderer.setSelectedSection(sp);
        redraw();
    }

    private void doNewWorld() {
        NewWorldDialog dlg = new NewWorldDialog();
        Optional<NewWorldDialog.Result> resOpt = dlg.showAndWait();
        if (resOpt.isEmpty()) return;

        NewWorldDialog.Result res = resOpt.get();

        WorldDef w = WorldDef.createDefault();
        w.name = res.name();
        w.size.widthX = res.widthX();
        w.size.heightY = res.heightY();
        w.size.depthZ = res.depthZ();

        String baseId = slugify(res.name());
        if (baseId.isBlank()) baseId = "world";
        String finalId = baseId;
        int suffix = 1;
        while (worldIdExists(finalId)) {
            suffix++;
            finalId = baseId + "-" + suffix;
        }
        w.id = finalId;

        this.world = w;
        renderer.setWorld(world);
        renderer.setSelectedSection(null);
        reloadSectionList();
        redraw();

        worldRepo.save(world);
        reloadWorldList();

        worldList.getItems().stream().filter(e -> e.id().equals(world.id)).findFirst().ifPresent(e -> worldList.getSelectionModel().select(e));
    }

    private boolean worldIdExists(String id) {
        return worldRepo.listJsonFiles().stream().anyMatch(p -> stripExt(p.getFileName().toString()).equals(id));
    }

    private String slugify(String name) {
        if (name == null) return "";
        String s = name.trim().toLowerCase();
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+", "").replaceAll("-+$", "");
        return s;
    }

    private void doSaveWorld() {
        if (world == null) return;
        if (world.id == null || world.id.isBlank()) {
            doNewWorld();
            return;
        }
        worldRepo.save(world);
        reloadWorldList();
        new Alert(Alert.AlertType.INFORMATION, "World saved as '" + world.id + "'.").showAndWait();
    }

    /* ============================================================
     *  Mouse → camera
     * ============================================================ */

    private void doDeleteSelectedWorld() {
        WorldEntry sel = worldList.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete world '" + sel.name() + "' (" + sel.id() + ")?", ButtonType.YES, ButtonType.NO);
        conf.setHeaderText("Confirm delete");
        Optional<ButtonType> choice = conf.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.YES) return;

        worldRepo.delete(sel.id());

        if (world != null && sel.id().equals(world.id)) {
            world = WorldDef.createDefault();
            renderer.setWorld(world);
            renderer.setSelectedSection(null);
            reloadSectionList();
            redraw();
        }

        reloadWorldList();
    }

    private void hookCanvasMouse() {
        canvas.setOnMousePressed(e -> {
            lastX = e.getX();
            lastY = e.getY();
            rightDragging = e.isSecondaryButtonDown();
        });

        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - lastX;
            double dy = e.getY() - lastY;
            lastX = e.getX();
            lastY = e.getY();

            if (rightDragging) {
                world.cameraYawDeg = (world.cameraYawDeg + dx * 0.25) % 360.0;
                world.cameraPitchDeg = clamp(world.cameraPitchDeg - dy * 0.25, 5.0, 85.0);
            } else {
                world.cameraPanX += dx;
                world.cameraPanY += dy;
            }
            redraw();
        });

        canvas.setOnScroll(e -> {
            double delta = e.getDeltaY();
            if (delta > 0) {
                world.cameraZoom = Math.min(4.0, world.cameraZoom * 1.15);
            } else if (delta < 0) {
                world.cameraZoom = Math.max(0.25, world.cameraZoom / 1.15);
            }
            redraw();
        });

        canvas.setOnMouseClicked(e -> {
            // only primary-click selection, ignore right-click-used-for-rotation
            if (!e.isPrimaryButtonDown()) return;

            SectionPlacement hit = renderer.hitTestSection(e.getX(), e.getY());
            if (hit != null) {
                // select in list
                for (SectionEntry se : sectionList.getItems()) {
                    if (se.section() == hit) {
                        sectionList.getSelectionModel().select(se);
                        break;
                    }
                }
                renderer.setSelectedSection(hit);
            } else {
                sectionList.getSelectionModel().clearSelection();
                renderer.setSelectedSection(null);
            }
            redraw();
        });
    }

    /* ============================================================
     *  Canvas DnD: drop sections into world
     * ============================================================ */

    private void hookCanvasDnD() {
        canvas.setOnDragOver(e -> {
            var db = e.getDragboard();
            if (db.hasContent(MAP_SECTION_FORMAT)) {
                e.acceptTransferModes(TransferMode.COPY);

                String mapId = (String) db.getContent(MAP_SECTION_FORMAT);
                if (mapId != null && !mapId.isBlank()) {
                    updateGhostFromScreen(mapId, e.getX(), e.getY());
                }

                e.consume();
            }
        });

        canvas.setOnDragExited(e -> {
            renderer.clearGhostSection();
            redraw();
        });

        canvas.setOnDragDropped(e -> {
            var db = e.getDragboard();
            boolean success = false;
            if (db.hasContent(MAP_SECTION_FORMAT)) {
                String mapId = (String) db.getContent(MAP_SECTION_FORMAT);
                MapDef mapDef = loadMapById(mapId);
                if (mapDef != null) {
                    updateGhostFromScreen(mapId, e.getX(), e.getY());
                    GhostSection ghost = renderer.getGhostSection();
                    if (ghost != null && ghost.map != null) {
                        SectionPlacement sp = world.addSection(ghost.map, ghost.wx, ghost.wy, ghost.wz);
                        world.clampSectionToWorld(sp);
                        renderer.clearGhostSection();
                        success = true;

                        reloadSectionList();
                        // select the newly added section (last one)
                        if (!world.sections.isEmpty()) {
                            SectionPlacement last = world.sections.get(world.sections.size() - 1);
                            for (SectionEntry entry : sectionList.getItems()) {
                                if (entry.section() == last) {
                                    sectionList.getSelectionModel().select(entry);
                                    renderer.setSelectedSection(last);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            redraw();
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private void updateGhostFromScreen(String mapId, double sx, double sy) {
        MapDef mapDef = loadMapById(mapId);
        if (mapDef == null) {
            renderer.clearGhostSection();
            return;
        }

        int[] tile = renderer.screenToWorldTileOnZPlane(0, sx, sy);
        int wx = 0, wy = 0, wz = 0;
        if (tile != null) {
            wx = tile[0];
            wy = tile[1];
        }

        SectionPlacement tmp = new SectionPlacement(mapDef.id, wx, wy, wz);
        tmp.map = mapDef;
        world.clampSectionToWorld(tmp);

        GhostSection ghost = new GhostSection(mapDef, tmp.wx, tmp.wy, tmp.wz);
        renderer.setGhostSection(ghost);
    }

    /* ============================================================
     *  Rendering
     * ============================================================ */

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        renderer.render(g);
    }

    /* ============================================================
     *  Helper types
     * ============================================================ */

    private record WorldEntry(String id, String name, Path path) {
    }

    private record MapEntry(String id, String name, Path path) {
    }

    private record SectionEntry(String label, SectionPlacement section) {
    }
}
