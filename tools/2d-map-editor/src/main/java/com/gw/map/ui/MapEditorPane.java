package com.gw.map.ui;

import com.gw.map.io.MapRepository;
import com.gw.map.model.MapDef;
import com.gw.map.model.PlaneHit;
import com.gw.map.model.SelectionState;
import com.gw.map.model.Size3i;
import com.gw.map.ui.dialog.LoadMapDialog;
import com.gw.map.ui.dialog.NewMapDialog;
import com.gw.map.ui.sidebar.MapSidebarPane;
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
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.Optional;

/**
 * Center pane: toolbar (New/Save/Load) and the isometric viewport with plane & tile selection.
 * Also shows a ghost overlay when dragging a template from the gallery.
 */
public class MapEditorPane extends BorderPane {
    private final MapRepository repo;

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton planeMode = new RadioButton("Plane select");
    private final RadioButton tileMode = new RadioButton("Tile select");

    private final SelectionState sel = new SelectionState();

    private final Canvas canvas = new Canvas(1000, 820);
    private final IsoRenderer renderer = new IsoRenderer(canvas);

    private final TabPane rightTabs = new TabPane();
    private final MapSidebarPane mapTab = new MapSidebarPane(this);
    private final PlaneSidebarPane planeTab = new PlaneSidebarPane();

    private MapDef map = MapDef.createDefault();

    // Ghost state for template drag (visual-only for now)
    private boolean showingGhost = false;
    private String ghostTemplateId = null;   // if you adopt payload, you could store parsed fields here

    // Mouse (pan on left, rotate yaw on right)
    private double lastX, lastY;
    private boolean rightDragging = false;

    public MapEditorPane(MapRepository repo) {
        this.repo = repo;
        setPadding(new Insets(8));
        setTop(buildToolbar());
        setCenter(wrapCanvas());
        setRight(buildRightTabs());
        setupDnD();
        wireSidebar();
    }

    public void setMap(MapDef def) {
        this.map = (def != null ? def : MapDef.createDefault());
        if (this.map.size == null) this.map.size = new Size3i(16, 16, 6);
        renderer.setMap(this.map);
        ensurePlaneIndexInBounds();
        redraw();
        updateSidebarLabels();
    }

    private Node wrapCanvas() {
        StackPane sp = new StackPane(canvas);
        sp.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f8f8, #f0f0f0);");
        sp.setOnMousePressed(this::onMousePressed);
        sp.setOnMouseDragged(this::onMouseDragged);
        sp.setOnMouseReleased(e -> {
            // end any ghost cursor if the user aborted a drag with mouse
            if (!showingGhost) setCursor(Cursor.DEFAULT);
            redraw();
        });
        return new ScrollPane(sp);
    }

    private ToolBar buildToolbar() {
        Button btnNew = new Button("New");
        btnNew.setOnAction(e -> doNew());
        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> doSave());
        Button btnLoad = new Button("Load");
        btnLoad.setOnAction(e -> doLoad());

        planeMode.setToggleGroup(modeGroup);
        tileMode.setToggleGroup(modeGroup);
        planeMode.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> onModeChanged());

        return new ToolBar(btnNew, btnSave, btnLoad, new Separator(Orientation.VERTICAL), new Label("Mode:"), planeMode, tileMode);
    }

    private Node buildRightTabs() {
        Tab tMap = new Tab("Map");
        tMap.setClosable(false);
        tMap.setContent(mapTab);

        Tab tPlane = new Tab("Plane");
        tPlane.setClosable(false);
        tPlane.setContent(planeTab);

        rightTabs.getTabs().addAll(tMap, tPlane);
        rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return rightTabs;
    }

    private void wireSidebar() {
        // Map tab wiring
        mapTab.basePlaneCombo.getSelectionModel().select(sel.base);
        mapTab.basePlaneCombo.valueProperty().addListener((obs, o, n) -> {
            sel.base = n;
            ensurePlaneIndexInBounds();
            sel.clearTiles();
            redraw();
            updateSidebarLabels();
            // When switching to TILE mode, select origin tile immediately
            if (modeGroup.getSelectedToggle() == tileMode) selectOriginTile();
        });

        mapTab.prevPlaneBtn.setOnAction(e -> {
            sel.index = Math.max(0, sel.index - 1);
            sel.clearTiles();
            redraw();
            updateSidebarLabels();
            if (isTileMode()) selectOriginTile();
        });

        mapTab.nextPlaneBtn.setOnAction(e -> {
            sel.index = Math.min(maxIndexForBase(), sel.index + 1);
            sel.clearTiles();
            redraw();
            updateSidebarLabels();
            if (isTileMode()) selectOriginTile();
        });

        mapTab.zoomInBtn.setOnAction(e -> {
            map.cameraZoom = Math.min(4.0, map.cameraZoom * 1.15);
            redraw();
        });
        mapTab.zoomOutBtn.setOnAction(e -> {
            map.cameraZoom = Math.max(0.25, map.cameraZoom / 1.15);
            redraw();
        });

        // Plane tab: Edit stub
        planeTab.editBtn.setOnAction(e -> onEditSelectedPlane());

        updateSidebarLabels();
    }

    private boolean isTileMode() {
        return modeGroup.getSelectedToggle() == tileMode;
    }

    private void updateSidebarLabels() {
        mapTab.planeIndexLabel.setText("Plane index: " + sel.index + " / " + maxIndexForBase());
    }

    private int maxIndexForBase() {
        if (map == null || map.size == null) return 0;
        return switch (sel.base) {
            case X -> Math.max(0, map.size.widthX - 1);
            case Y -> Math.max(0, map.size.heightY - 1);
            case Z -> Math.max(0, map.size.depthZ - 1);
        };
    }

    private void ensurePlaneIndexInBounds() {
        sel.index = Math.max(0, Math.min(sel.index, maxIndexForBase()));
    }

    private void onModeChanged() {
        if (isTileMode()) {
            // auto-select origin tile on current plane
            selectOriginTile();
        } else {
            sel.clearTiles();
        }
        redraw();
    }

    private void selectOriginTile() {
        sel.clearTiles();
        sel.selectedTiles.add(new SelectionState.TileKey(0, 0));
    }

    // ---------- DnD ghost (visual only for now) ----------

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

    private void setupDnD() {
        canvas.setOnDragOver(e -> {
            // Accept either the legacy TEMPLATE_ID_FORMAT or the new PAYLOAD_FORMAT
            boolean hasId = e.getDragboard().hasContent(TemplateGalleryPaneLegacy.TEMPLATE_ID_FORMAT);
            boolean hasPayload = e.getDragboard().hasContent(TemplateGalleryPane.PAYLOAD_FORMAT);
            if (hasId || hasPayload) {
                e.acceptTransferModes(TransferMode.COPY);
                showingGhost = true;
                e.consume();
                redraw(e.getX(), e.getY());
            }
        });
        canvas.setOnDragExited(e -> endTemplateDragPreview());
        canvas.setOnDragDropped(e -> {
            var db = e.getDragboard();
            boolean hasId = db.hasContent(TemplateGalleryPaneLegacy.TEMPLATE_ID_FORMAT);
            boolean hasPayload = db.hasContent(TemplateGalleryPane.PAYLOAD_FORMAT);

            if (hasId) {
                // Legacy path: just a templateId string
                Object val = db.getContent(TemplateGalleryPaneLegacy.TEMPLATE_ID_FORMAT);
                ghostTemplateId = (val == null) ? null : val.toString();
                // TODO: handle drop payload if you want to create instances here
                e.setDropCompleted(true);
            } else if (hasPayload) {
                // New payload format: parse if needed
                String payload = (String) db.getContent(TemplateGalleryPane.PAYLOAD_FORMAT);
                // Example: templateId|regionIndex|tileW|tileH|x|y|w|h|scale
                String[] parts = payload != null ? payload.split("\\|") : new String[0];
                if (parts.length >= 9) {
                    ghostTemplateId = parts[0];
                    // TODO: parse & use region/rect/scale when you hook up placement
                    e.setDropCompleted(true);
                }
            }
            endTemplateDragPreview();
        });
    }

    private void onMousePressed(javafx.scene.input.MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
        rightDragging = e.getButton() == MouseButton.SECONDARY;

        if (!rightDragging) {
            if (modeGroup.getSelectedToggle() == planeMode) {
                PlaneHit hit = renderer.hitTestPlane(e.getX(), e.getY());
                if (hit != null) {
                    // FIX: PlaneHit is a class with fields, not a record — use field access
                    sel.base = hit.base;
                    sel.index = hit.index;
                    sel.clearTiles();
                    updateSidebarLabels();
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

    private void onMouseDragged(javafx.scene.input.MouseEvent e) {
        double dx = e.getX() - lastX, dy = e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        if (rightDragging) {
            map.cameraYawDeg   = (map.cameraYawDeg   + dx * 0.25) % 360.0;
            map.cameraPitchDeg = clamp(map.cameraPitchDeg - dy * 0.25, 5.0, 85.0); // pitch up/down
        } else {
            map.cameraPanX += dx;
            map.cameraPanY += dy; // pan
        }
        redraw(e.getX(), e.getY());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }


    // ---------- Commands ----------
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

    private void doSave() {
        repo.save(map);
        new Alert(Alert.AlertType.INFORMATION, "Saved.").showAndWait();
    }

    private void doLoad() {
        LoadMapDialog dlg = new LoadMapDialog(repo);
        dlg.showAndWait().ifPresent(this::setMap);
    }

    // Plane edit stub
    private void onEditSelectedPlane() {
        // TODO: open plane editor for (sel.base, sel.index)
        new Alert(Alert.AlertType.INFORMATION, "Edit stub for plane " + sel.base + "=" + sel.index).showAndWait();
    }

    // ---------- Rendering ----------
    private void redraw() {
        redraw(Double.NaN, Double.NaN);
    }

    private void redraw(double ghostX, double ghostY) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        renderer.render(g, map, sel, isTileMode(), showingGhost ? ghostTemplateId : null, (!Double.isNaN(ghostX) && !Double.isNaN(ghostY)) ? new double[]{ghostX, ghostY} : null);
    }

    /**
     * Temporary shim so code compiles even if you haven’t migrated your left-bar to the new payload yet.
     * Remove once everything uses TemplateGalleryPane.PAYLOAD_FORMAT.
     */
    private static final class TemplateGalleryPaneLegacy {
        static final javafx.scene.input.DataFormat TEMPLATE_ID_FORMAT = new javafx.scene.input.DataFormat("application/x-template-id");
    }
}
