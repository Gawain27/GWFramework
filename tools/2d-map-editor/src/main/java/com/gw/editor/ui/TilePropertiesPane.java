package com.gw.editor.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateDef.Orientation;
import com.gw.editor.template.TemplateDef.ShapeType;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Point;
import java.util.*;

public class TilePropertiesPane extends VBox {

    private final Map<Point, TemplateDef.TileDef> stagedTiles = new HashMap<>();
    private final List<TemplateDef.RegionDef> stagedRegions = new ArrayList<>();
    private TemplateDef boundTemplate;

    // ---- TILE UI ----
    private final Label hdr = new Label("Tile Properties");

    private final TextField tagField = new TextField();
    private BooleanProperty complexProp;
    private final CheckBox gateBox = new CheckBox("Gate (green)");
    private final CheckBox solidBox = new CheckBox("Solid (has collision)");
    private final ComboBox<ShapeType> shapeBox = new ComboBox<>();
    private final ComboBox<Orientation> orientBox = new ComboBox<>();
    private final Spinner<Double> floatSpinner = new Spinner<>(0.0, 10.0, 0.0, 0.1);

    private final Set<Point> selection = new LinkedHashSet<>();

    private Runnable editsChanged; // notify viewer to repaint
    private SelectionSupplier selectionSupplier; // to read current selection bbox for regions

    // ---- REGIONS UI ----
    private final CheckBox markRegionBox = new CheckBox("Mark current selection as region");
    private final TextField regionIdField = new TextField();
    private final Button addRegionBtn = new Button("Add Region");
    private final ListView<TemplateDef.RegionDef> regionsList = new ListView<>();
    private final Button removeRegionBtn = new Button("Remove");

    public interface SelectionSupplier { Set<Point> getSelection(); }

    public TilePropertiesPane() {
        setSpacing(10);
        setPadding(new Insets(8));

        shapeBox.getItems().setAll(ShapeType.values());
        shapeBox.setValue(ShapeType.RECT_FULL);
        orientBox.getItems().setAll(Orientation.values());
        orientBox.setValue(Orientation.UP);

        var tagBox = new VBox(4, tagField, new Label("Tag"));
        var gateWrap = new VBox(2, gateBox, new Label("Gate"));
        var solidWrap = new VBox(2, solidBox, new Label("Collision enabled"));
        var shapeWrap = new VBox(4, shapeBox, new Label("Shape"));
        var orientWrap = new VBox(4, orientBox, new Label("Orientation"));

        // Tag/custom disabled when multi-select
        tagField.disableProperty().bind(new javafx.beans.binding.BooleanBinding() {
            { bind(); } @Override protected boolean computeValue() { return selection.size() > 1; }
        });
        floatSpinner.disableProperty().bind(tagField.disableProperty());

        // Shape/orientation disabled when not solid
        shapeBox.disableProperty().bind(solidBox.selectedProperty().not());
        orientBox.disableProperty().bind(solidBox.selectedProperty().not());

        getChildren().addAll(hdr, tagBox, gateWrap, solidWrap, shapeWrap, orientWrap);

        // listeners – stage without touching template
        tagField.textProperty().addListener((obs, o, n) -> stageForSelection(true, false, false));
        floatSpinner.valueProperty().addListener((obs, o, n) -> stageForSelection(true, false, false));
        gateBox.selectedProperty().addListener((obs, o, n) -> stageForSelection(false, false, true));
        solidBox.selectedProperty().addListener((obs, o, n) -> stageForSelection(false, true, false));
        shapeBox.valueProperty().addListener((obs, o, n) -> stageForSelection(false, true, false));
        orientBox.valueProperty().addListener((obs, o, n) -> stageForSelection(false, true, false));

        // ---- Regions UI ----
        regionIdField.setPromptText("region-id");
        var regionIdBox = new VBox(4, regionIdField, new Label("Region Id"));

        addRegionBtn.setOnAction(e -> tryAddRegionFromSelection());
        removeRegionBtn.setOnAction(e -> {
            var sel = regionsList.getSelectionModel().getSelectedItem();
            if (sel != null) { stagedRegions.remove(sel); refreshRegionsList(); fireEdit(); }
        });

        HBox regionActions = new HBox(8, addRegionBtn, removeRegionBtn);
        var regionSection = new VBox(8,
            new Label("Regions"),
            markRegionBox,
            regionIdBox,
            regionActions,
            regionsList
        );
        regionSection.setPadding(new Insets(8,0,0,0));

        getChildren().add(regionSection);
        VBox.setVgrow(regionsList, Priority.ALWAYS);
    }

    public void setSelectionSupplier(SelectionSupplier sup) { this.selectionSupplier = sup; }

    public void bindComplexProperty(BooleanProperty complex) {
        this.complexProp = complex;
        markRegionBox.disableProperty().unbind();
        if (complex != null) {
            markRegionBox.disableProperty().bind(complex.not());
        } else {
            markRegionBox.setDisable(true);
        }
    }

    public void bindTo(TemplateDef t) {
        this.boundTemplate = t;
        clearEdits();
        selection.clear();
        clearUI();
        // seed regions list from template (staged copy)
        if (t != null && t.regions != null) {
            for (var r : t.regions) stagedRegions.add(new TemplateDef.RegionDef(r.id, r.x0, r.y0, r.x1, r.y1));
        }
        refreshRegionsList();
        fireEdit(); // redraw with any existing regions
    }

    public void setEditsChangedCallback(Runnable r) { this.editsChanged = r; }

    /** Viewer provides the current selection (single or multi). */
    public void showSelection(Set<Point> sel) {
        selection.clear();
        if (sel != null) selection.addAll(sel);

        if (selection.isEmpty()) { clearUI(); return; }

        if (selection.size() == 1) {
            Point p = selection.iterator().next();
            TemplateDef.TileDef src = getEffectiveTile(p.x, p.y);
            if (src == null) {
                tagField.setText("");
                floatSpinner.getValueFactory().setValue(0.0);
                gateBox.setSelected(false);
                solidBox.setSelected(false);
                shapeBox.setValue(ShapeType.RECT_FULL);
                orientBox.setValue(Orientation.UP);
            } else {
                tagField.setText(src.tag == null ? "" : src.tag);
                floatSpinner.getValueFactory().setValue((double)src.customFloat);
                gateBox.setSelected(src.gate);
                solidBox.setSelected(src.solid);
                shapeBox.setValue(src.shape == null ? ShapeType.RECT_FULL : src.shape);
                orientBox.setValue(src.orientation == null ? Orientation.UP : src.orientation);
            }
        } else {
            var tiles = selection.stream().map(p -> getEffectiveTile(p.x, p.y)).filter(Objects::nonNull).toList();
            gateBox.setSelected(!tiles.isEmpty() && tiles.stream().allMatch(t -> t.gate));
            solidBox.setSelected(!tiles.isEmpty() && tiles.stream().allMatch(t -> t.solid));
            shapeBox.setValue(tiles.isEmpty() ? ShapeType.RECT_FULL
                : tiles.get(0).shape == null ? ShapeType.RECT_FULL : tiles.get(0).shape);
            orientBox.setValue(tiles.isEmpty() ? Orientation.UP
                : tiles.get(0).orientation == null ? Orientation.UP : tiles.get(0).orientation);

            tagField.setText("");
            floatSpinner.getValueFactory().setValue(0.0);
        }
    }

    /** Returns staged version if present, otherwise template version; null if none. */
    public TemplateDef.TileDef getEffectiveTile(int gx, int gy) {
        TemplateDef.TileDef stagedTile = stagedTiles.get(new Point(gx, gy));
        if (stagedTile != null) return stagedTile;
        if (boundTemplate == null) return null;
        return boundTemplate.tileAt(gx, gy);
    }

    public void clearEdits() {
        stagedTiles.clear();
        stagedRegions.clear();
        refreshRegionsList();
    }

    /** Merge staged edits into the target template (called on Save). */
    public void applyEditsTo(TemplateDef target) {
        if (target == null) return;
        // tiles
        for (var e : stagedTiles.entrySet()) {
            int gx = e.getKey().x, gy = e.getKey().y;
            TemplateDef.TileDef src = e.getValue();
            TemplateDef.TileDef dst = target.ensureTile(gx, gy);

            dst.tag = src.tag;
            dst.customFloat = src.customFloat;
            dst.gate = src.gate;
            dst.solid = src.solid;
            dst.shape = src.shape;
            dst.orientation = src.orientation;
        }
        // regions – replace with staged snapshot
        target.regions = new ArrayList<>();
        for (var r : stagedRegions) {
            target.regions.add(new TemplateDef.RegionDef(r.id, r.x0, r.y0, r.x1, r.y1));
        }
    }

    public List<TemplateDef.RegionDef> getEffectiveRegions() {
        return Collections.unmodifiableList(stagedRegions);
    }

    private void clearUI() {
        tagField.setText("");
        gateBox.setSelected(false);
        solidBox.setSelected(false);
        shapeBox.setValue(ShapeType.RECT_FULL);
        orientBox.setValue(Orientation.UP);
        floatSpinner.getValueFactory().setValue(0.0);
        markRegionBox.setSelected(false);
        regionIdField.setText("");
        regionsList.getSelectionModel().clearSelection();
    }

    /**
     * Stage edits for current selection.
     * @param singleOnly apply only if single-selected (tag/custom)
     * @param collisionOnly apply only Solid/Shape/Orientation (multi-friendly)
     * @param gateOnly apply only Gate (multi-friendly)
     */
    private void stageForSelection(boolean singleOnly, boolean collisionOnly, boolean gateOnly) {
        if (selection.isEmpty()) return;

        boolean isMulti = selection.size() > 1;
        if (singleOnly && isMulti) return; // keep tag/custom single-only

        for (Point p : selection) {
            TemplateDef.TileDef base = getEffectiveTile(p.x, p.y);
            TemplateDef.TileDef copy = baseCopyOrNew(base, p);

            if (!collisionOnly && !gateOnly) {
                copy.tag = tagField.getText();
                copy.customFloat = floatSpinner.getValue().floatValue();
            }
            if (gateOnly || !isMulti) {
                copy.gate = gateBox.isSelected();
            }
            if (collisionOnly || !isMulti) {
                copy.solid = solidBox.isSelected();
                copy.shape = shapeBox.getValue();
                copy.orientation = orientBox.getValue();
            }

            stagedTiles.put(new Point(p), copy);
        }

        fireEdit();
    }

    // ---------- COPY / PASTE API ----------

    private TemplateDef.TileDef clipboard = null;

    public void copyFrom(Point src) {
        if (src == null) { clipboard = null; return; }
        clipboard = deepCopy(getEffectiveTile(src.x, src.y));
    }

    public void pasteTo(Set<Point> targets) {
        if (clipboard == null || targets == null || targets.isEmpty()) return;

        boolean multi = targets.size() > 1;
        for (Point p : targets) {
            TemplateDef.TileDef base = getEffectiveTile(p.x, p.y);
            TemplateDef.TileDef copy = baseCopyOrNew(base, p);

            if (!multi) {
                // single: all fields
                copy.tag = clipboard.tag;
                copy.customFloat = clipboard.customFloat;
            }
            // multi/single: collision + gate
            copy.gate = clipboard.gate;
            copy.solid = clipboard.solid;
            copy.shape = clipboard.shape;
            copy.orientation = clipboard.orientation;

            stagedTiles.put(new Point(p), copy);
        }
        fireEdit();
    }

    private static TemplateDef.TileDef deepCopy(TemplateDef.TileDef src) {
        if (src == null) return null;
        TemplateDef.TileDef c = new TemplateDef.TileDef(src.gx, src.gy);
        c.tag = src.tag;
        c.customFloat = src.customFloat;
        c.gate = src.gate;
        c.solid = src.solid;
        c.shape = src.shape;
        c.orientation = src.orientation;
        return c;
    }

    private static TemplateDef.TileDef baseCopyOrNew(TemplateDef.TileDef base, Point p) {
        TemplateDef.TileDef c = new TemplateDef.TileDef(p.x, p.y);
        if (base != null) {
            c.tag = base.tag;
            c.customFloat = base.customFloat;
            c.gate = base.gate;
            c.solid = base.solid;
            c.shape = base.shape;
            c.orientation = base.orientation;
        }
        return c;
    }

    // ---------- REGIONS ----------

    private void tryAddRegionFromSelection() {
        if (!markRegionBox.isSelected() || selectionSupplier == null) return;
        Set<Point> sel = selectionSupplier.getSelection();
        if (sel == null || sel.isEmpty()) return;

        int minX = sel.stream().mapToInt(p -> p.x).min().orElse(0);
        int minY = sel.stream().mapToInt(p -> p.y).min().orElse(0);
        int maxX = sel.stream().mapToInt(p -> p.x).max().orElse(0);
        int maxY = sel.stream().mapToInt(p -> p.y).max().orElse(0);

        String rid = regionIdField.getText();
        if (rid == null || rid.isBlank()) rid = "region_" + (stagedRegions.size() + 1);

        stagedRegions.add(new TemplateDef.RegionDef(rid, minX, minY, maxX, maxY));
        refreshRegionsList();
        fireEdit();
    }

    private void refreshRegionsList() {
        regionsList.getItems().setAll(stagedRegions);
    }

    private void fireEdit() { if (editsChanged != null) editsChanged.run(); }
}
