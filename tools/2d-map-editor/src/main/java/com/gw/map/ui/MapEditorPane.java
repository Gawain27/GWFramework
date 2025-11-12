package com.gw.map.ui;

import com.gw.editor.template.TemplateRepository;
import com.gw.map.io.MapRepository;
import com.gw.map.model.MapDef;
import com.gw.map.model.Plane2DMap;
import com.gw.map.model.PlaneHit;
import com.gw.map.model.SelectionState;
import com.gw.map.model.Size3i;
import com.gw.map.ui.dialog.LoadMapDialog;
import com.gw.map.ui.dialog.NewMapDialog;
import com.gw.map.ui.plane.PlaneCanvasPane;
import com.gw.map.ui.sidebar.MapSidebarPane;
import com.gw.map.ui.sidebar.MapSidebarPane.UiBasePlane;
import com.gw.map.ui.sidebar.PlaneSidebarPane;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Main editor shell:
 * - Center: TabPane (3D view + per-plane editors).
 * - Left: Template Gallery (collapsible only).
 * - Right: Single sidebar area. Shows MapSidebarPane on 3D tab, PlaneSidebarPane on plane tabs.
 */
public class MapEditorPane extends BorderPane {

    private final MapRepository repo;

    // Modes
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton planeMode = new RadioButton("Plane select");
    private final RadioButton tileMode = new RadioButton("Tile select");

    // Selection for 3D view
    private final SelectionState sel = new SelectionState();

    // 3D canvas & renderer
    private final Canvas canvas = new Canvas(1000, 820);
    private final IsoRenderer renderer = new IsoRenderer(canvas);

    // Center tabs
    private final TabPane centerTabs = new TabPane();
    private final Tab tab3D = new Tab("3D View");

    // Right side panes (we swap these into setRight(...))
    private final MapSidebarPane mapSidebar = new MapSidebarPane();
    private final PlaneSidebarPane planeSidebar = new PlaneSidebarPane(new TemplateRepository());
    private final Map<Tab, PlaneTabCtx> planeTabs = new HashMap<>();
    // Left gallery (collapsible)
    private Node leftGallery = null;
    private boolean leftVisible = true;
    // Map
    private MapDef map = MapDef.createDefault();
    // Drag ghost (3D)
    private boolean showingGhost = false;
    private String ghostTemplateId = null;
    // Mouse (3D)
    private double lastX, lastY;
    private boolean rightDragging = false;
    public MapEditorPane(MapRepository repo) {
        this.repo = repo;

        setPadding(new Insets(8));
        setTop(buildToolbar());

        // Center: 3D view tab
        tab3D.setClosable(false);
        tab3D.setContent(wrapCanvas());
        centerTabs.getTabs().add(tab3D);
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        setCenter(centerTabs);

        // Active tab listener → swap right sidebar content
        centerTabs.getSelectionModel().selectedItemProperty().addListener((o, oldT, newT) -> updateRightSidebarForActiveTab());

        setupDnD();
        wireMapSidebar();
        // Initial right content = map sidebar
        setRight(mapSidebar);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Provide the left template gallery. We only collapse/show this node.
     */
    public void setLeftGallery(Node gallery) {
        this.leftGallery = gallery;
        if (leftVisible) {
            setLeft(leftGallery);
            if (leftGallery != null) leftGallery.setManaged(true);
        }
    }

    public void setMap(MapDef def) {
        this.map = def != null ? def : MapDef.createDefault();
        renderer.setMap(this.map);
        ensurePlaneIndexInBounds();
        redraw();
        updateMapSidebarLabels();
    }

    // ---------- UI ----------

    private Node wrapCanvas() {
        StackPane sp = new StackPane(canvas);
        sp.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f8f8, #f0f0f0);");
        sp.setOnMousePressed(this::onMousePressed3D);
        sp.setOnMouseDragged(this::onMouseDragged3D);
        sp.setOnMouseReleased(e -> redraw());
        return new ScrollPane(sp);
    }

    private ToolBar buildToolbar() {
        Button btnNew = new Button("New");
        btnNew.setOnAction(e -> doNew());
        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> doSave());
        Button btnLoad = new Button("Load");
        btnLoad.setOnAction(e -> doLoad());

        // Collapsible gallery only
        ToggleButton tLeft = new ToggleButton("Toggle Gallery");
        tLeft.setSelected(true);
        tLeft.setOnAction(e -> toggleLeft(tLeft.isSelected()));

        planeMode.setToggleGroup(modeGroup);
        tileMode.setToggleGroup(modeGroup);
        planeMode.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> onModeChanged());

        return new ToolBar(btnNew, btnSave, btnLoad, new Separator(Orientation.VERTICAL), tLeft, new Separator(Orientation.VERTICAL), new Label("Mode:"), planeMode, tileMode);
    }

    private void toggleLeft(boolean show) {
        leftVisible = show;
        if (leftGallery == null) return;
        if (show) {
            setLeft(leftGallery);
            leftGallery.setManaged(true);
        } else {
            setLeft(null);
            leftGallery.setManaged(false);
        }
    }

    // ---------- Map sidebar wiring (3D) ----------

    private void wireMapSidebar() {
        // Base plane combobox maps directly: X=x=k, Y=y=k, Z=z=k
        mapSidebar.setOnBasePlaneChanged((UiBasePlane ui) -> {
            sel.base = switch (ui) {
                case X -> SelectionState.BasePlane.X;
                case Y -> SelectionState.BasePlane.Y;
                case Z -> SelectionState.BasePlane.Z;
            };
            ensurePlaneIndexInBounds();
            sel.clearTiles();
            redraw();
            updateMapSidebarLabels();
            if (isTileMode()) selectOriginTile();
        });

        mapSidebar.setOnPrevPlane(() -> {
            sel.index = Math.max(0, sel.index - 1);
            sel.clearTiles();
            redraw();
            updateMapSidebarLabels();
            if (isTileMode()) selectOriginTile();
        });

        mapSidebar.setOnNextPlane(() -> {
            sel.index = Math.min(maxIndexForBase(), sel.index + 1);
            sel.clearTiles();
            redraw();
            updateMapSidebarLabels();
            if (isTileMode()) selectOriginTile();
        });

        mapSidebar.setOnZoomIn(() -> {
            map.cameraZoom = Math.min(4.0, map.cameraZoom * 1.15);
            redraw();
        });
        mapSidebar.setOnZoomOut(() -> {
            map.cameraZoom = Math.max(0.25, map.cameraZoom / 1.15);
            redraw();
        });

        // Edit current plane → open a plane tab (canvas only) and switch right sidebar to PlaneSidebarPane bound to it
        mapSidebar.setOnEditCurrentPlane(this::openCurrentPlaneEditor);

        updateMapSidebarLabels();
    }

    private boolean isTileMode() {
        return modeGroup.getSelectedToggle() == tileMode;
    }

    private void updateMapSidebarLabels() {
        mapSidebar.setPlaneIndexText("Plane index: " + sel.index + " / " + maxIndexForBase());
    }

    private int maxIndexForBase() {
        return switch (sel.base) {
            case X -> map.size.widthX - 1;
            case Y -> map.size.heightY - 1;
            case Z -> map.size.depthZ - 1;
        };
    }

    private void ensurePlaneIndexInBounds() {
        sel.index = Math.max(0, Math.min(sel.index, maxIndexForBase()));
    }

    private void onModeChanged() {
        if (isTileMode()) selectOriginTile();
        else sel.clearTiles();
        redraw();
    }

    private void selectOriginTile() {
        sel.clearTiles();
        sel.selectedTiles.add(new SelectionState.TileKey(0, 0));
    }

    // ---------- Plane editor tabs ----------

    private void openCurrentPlaneEditor() {
        String key = sel.base + "=" + sel.index;

        // if already open, select it
        for (Tab t : centerTabs.getTabs()) {
            if (key.equals(t.getUserData())) {
                centerTabs.getSelectionModel().select(t);
                return;
            }
        }

        // Build per-plane model & canvas
        Plane2DMap plane = Plane2DMap.from3D(map, sel.base, sel.index);
        PlaneCanvasPane canvasPane = new PlaneCanvasPane(new TemplateRepository());
        canvasPane.bindMap(plane);

        // NO sidebar in the tab content! Only canvas.
        ScrollPane scroller = new ScrollPane(canvasPane);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);

        Tab editorTab = new Tab("Plane " + key, scroller);
        editorTab.setUserData(key);
        centerTabs.getTabs().add(editorTab);

        // Track its context so we can bind PlaneSidebarPane when selected
        planeTabs.put(editorTab, new PlaneTabCtx(plane, canvasPane));

        centerTabs.getSelectionModel().select(editorTab);
        updateRightSidebarForActiveTab(); // switch right sidebar to PlaneSidebarPane bound to this plane
    }

    private void updateRightSidebarForActiveTab() {
        Tab active = centerTabs.getSelectionModel().getSelectedItem();
        if (active == tab3D || active == null) {
            // 3D view → show MapSidebarPane
            setRight(mapSidebar);
            return;
        }
        PlaneTabCtx ctx = planeTabs.get(active);
        if (ctx != null) {
            // Bind PlaneSidebarPane to this plane and hook redraw callback
            planeSidebar.bindMap(ctx.plane);
            planeSidebar.setOnRequestRedraw(ctx.canvas::redraw);
            // Selection bridge: when canvas changes selection, refresh sidebar data
            ctx.canvas.setOnSelectionChanged(planeSidebar::refresh);
            setRight(planeSidebar);
        } else {
            // Fallback: show Map sidebar
            setRight(mapSidebar);
        }
    }

    // ---------- DnD ghost on 3D canvas ----------

    private void setupDnD() {
        canvas.setOnDragOver(e -> {
            boolean hasPayload = e.getDragboard().hasContent(TemplateGalleryPane.PAYLOAD_FORMAT);
            if (hasPayload) {
                e.acceptTransferModes(TransferMode.COPY);
                showingGhost = true;
                e.consume();
                redraw(e.getX(), e.getY());
            }
        });
        canvas.setOnDragExited(e -> endTemplateDragPreview());
        canvas.setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasContent(TemplateGalleryPane.PAYLOAD_FORMAT)) {
                e.setDropCompleted(true);
            }
            endTemplateDragPreview();
        });
    }

    public void beginTemplateDragPreview(String templateId) {
        this.ghostTemplateId = templateId;
        this.showingGhost = true;
        setCursor(Cursor.CROSSHAIR);
    }

    private void endTemplateDragPreview() {
        this.ghostTemplateId = null;
        this.showingGhost = false;
        setCursor(Cursor.DEFAULT);
        redraw();
    }

    // ---------- Mouse on 3D ----------

    private void onMousePressed3D(javafx.scene.input.MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        rightDragging = e.getButton() == MouseButton.SECONDARY;

        if (!rightDragging) {
            if (!isTileMode()) {
                PlaneHit hit = renderer.hitTestPlane(e.getX(), e.getY());
                if (hit != null) {
                    sel.base = hit.base;
                    sel.index = hit.index;
                    sel.clearTiles();
                    updateMapSidebarLabels();
                    redraw();
                }
            } else {
                int[] ab = renderer.screenToPlaneTile(sel.base, sel.index, e.getX(), e.getY());
                if (ab != null) {
                    sel.clearTiles();
                    sel.selectedTiles.add(new SelectionState.TileKey(ab[0], ab[1]));
                    redraw();
                }
            }
        }
    }

    private void onMouseDragged3D(javafx.scene.input.MouseEvent e) {
        double dx = e.getX() - lastX, dy = e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        if (rightDragging) {
            map.cameraYawDeg = (map.cameraYawDeg + dx * 0.25) % 360.0;
            map.cameraPitchDeg = clamp(map.cameraPitchDeg - dy * 0.25, 5.0, 85.0);
        } else {
            map.cameraPanX += dx;
            map.cameraPanY += dy;
        }
        redraw(e.getX(), e.getY());
    }

    private void doNew() {
        NewMapDialog dlg = new NewMapDialog();
        Optional<NewMapDialog.Result> res = dlg.showAndWait();
        res.ifPresent(r -> {
            MapDef m = new MapDef();
            m.id = r.mapId;
            m.name = r.mapName;
            m.size = new Size3i(r.widthX, r.heightY, r.depthZ);
            setMap(m);
        });
    }

    // ---------- Commands ----------

    private void doSave() {
        repo.save(map);
        new Alert(Alert.AlertType.INFORMATION, "Saved.").showAndWait();
    }

    private void doLoad() {
        LoadMapDialog dlg = new LoadMapDialog(repo);
        dlg.showAndWait().ifPresent(this::setMap);
    }

    private void redraw() {
        redraw(Double.NaN, Double.NaN);
    }

    // ---------- Rendering ----------

    private void redraw(double ghostX, double ghostY) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        renderer.render(g, map, sel, isTileMode(), showingGhost ? ghostTemplateId : null, (!Double.isNaN(ghostX) && !Double.isNaN(ghostY)) ? new double[]{ghostX, ghostY} : null);
    }

    // Track open plane tabs → model/canvas so we can bind the sidebar when active
        private record PlaneTabCtx(Plane2DMap plane, PlaneCanvasPane canvas) {
    }
}
