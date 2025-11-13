package com.gw.map.ui.sidebar;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateGateUtils;
import com.gw.map.model.Plane2DMap;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plane tab (scrollable): instance info, rendering layer select, layer management, gates & links,
 * and rotation buttons for the selected placement (±90° step).
 */
public class PlaneSidebarPane extends ScrollPane {

    private final TemplateRepository repo;
    private Plane2DMap map;
    private Plane2DMap.Placement sel;

    private final VBox root = new VBox(8);

    // Instance header
    private final Label lblTid = new Label("-");
    private final Label lblRegion = new Label("-");
    private final Label lblPos = new Label("-");

    // Instance rendering
    private final ComboBox<Integer> layerCombo = new ComboBox<>();
    private final Button rotCwBtn  = new Button("Rotate +90°");
    private final Button rotCcwBtn = new Button("Rotate -90°");

    // Layer management (for the plane)
    private final ListView<Integer> layerList = new ListView<>();
    private final Button addLayerBtn    = new Button("Add Layer");
    private final Button removeLayerBtn = new Button("Remove Selected Layer");

    // Gates
    private final ListView<String> gateList = new ListView<>();
    private final TextField gateNameField = new TextField();
    private final Button saveGateNameBtn = new Button("Save Gate Name");

    // Links
    private final ListView<String> linkList = new ListView<>();
    private final TextField linkNameField = new TextField();
    private final ComboBox<String> gateSearch = new ComboBox<>();
    private final Button addLinkBtn = new Button("Add Link");
    private final Button removeLinkBtn = new Button("Remove Selected Link");
    private final Button saveLinkNameBtn = new Button("Save Link Name");

    private final List<Plane2DMap.GateLink> linkVm = new ArrayList<>();
    private List<List<int[]>> cachedIslands = List.of();
    private Runnable onRequestRedraw = () -> {};

    public PlaneSidebarPane(TemplateRepository repo) {
        this.repo = (repo != null ? repo : new TemplateRepository());

        setContent(root);
        setFitToWidth(true);

        root.setPadding(new Insets(10));
        root.setFillWidth(true);

        root.getChildren().add(buildHeader());
        root.getChildren().add(buildInstanceRow());
        root.getChildren().add(buildLayerManager());
        root.getChildren().add(buildGates());
        root.getChildren().add(buildLinks());

        gateSearch.setEditable(true);

        layerCombo.setOnAction(e -> {
            if (map == null || sel == null) return;
            Integer newLayer = layerCombo.getValue();
            if (newLayer == null) return;
            if (newLayer < 0 || newLayer >= map.layers.size()) return;
            sel.layer = newLayer;
            onRequestRedraw.run();
        });

        rotCwBtn.setOnAction(e -> {
            if (sel == null) return;
            sel.rotQ = (sel.rotQ + 1) & 3;
            onRequestRedraw.run();
            refresh(sel);
        });
        rotCcwBtn.setOnAction(e -> {
            if (sel == null) return;
            sel.rotQ = (sel.rotQ + 3) & 3; // -90
            onRequestRedraw.run();
            refresh(sel);
        });

        addLayerBtn.setOnAction(e -> addLayer());
        removeLayerBtn.setOnAction(e -> removeSelectedLayer());

        saveGateNameBtn.setOnAction(e -> {
            int idx = gateList.getSelectionModel().getSelectedIndex();
            if (map == null || sel == null || idx < 0) return;
            var ref = new Plane2DMap.GateRef(sel.pid, idx);
            var meta = map.ensureGateMeta(ref);
            meta.name = norm(gateNameField.getText());
            refreshGateList();
            refreshLinkList();
        });

        addLinkBtn.setOnAction(e -> addLinkFromSearch());
        removeLinkBtn.setOnAction(e -> removeSelectedLink());
        saveLinkNameBtn.setOnAction(e -> renameSelectedLink());

        linkList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (nv == null || nv.intValue() < 0 || nv.intValue() >= linkVm.size()) {
                linkNameField.clear();
            } else {
                var gl = linkVm.get(nv.intValue());
                linkNameField.setText(gl.name == null ? "" : gl.name);
            }
        });
    }

    public void setOnRequestRedraw(Runnable r) { this.onRequestRedraw = (r != null ? r : () -> {}); }
    public void bindMap(Plane2DMap map) {
        this.map = map;
        // Seed layer UI
        layerList.setItems(FXCollections.observableArrayList(map == null ? FXCollections.<Integer>observableArrayList() : map.layers));
        refresh(null);
    }
    public void refresh(Plane2DMap.Placement selected) {
        this.sel = selected;

        if (map == null || sel == null) {
            lblTid.setText("-"); lblRegion.setText("-"); lblPos.setText("-");
            layerCombo.getItems().setAll();
            gateList.getItems().setAll(); linkList.getItems().setAll();
            gateNameField.clear(); gateSearch.getItems().setAll(); linkNameField.clear();
            addLinkBtn.setDisable(true); removeLinkBtn.setDisable(true);
            saveGateNameBtn.setDisable(true); saveLinkNameBtn.setDisable(true);
            rotCwBtn.setDisable(true); rotCcwBtn.setDisable(true);
            return;
        }

        rotCwBtn.setDisable(false);
        rotCcwBtn.setDisable(false);

        lblTid.setText(sel.templateId);
        lblRegion.setText(sel.regionIndex < 0 ? "(whole)" : ("region " + sel.regionIndex));
        lblPos.setText("(" + sel.gx + "," + sel.gy + ") • " + sel.wTiles + "×" + sel.hTiles + " tiles  •  rot " + (sel.rotQ*90) + "°");

        // Per-instance layer selector
        layerCombo.getItems().setAll(map.layers);
        layerCombo.getSelectionModel().select(Math.max(0, Math.min(sel.layer, map.layers.size()-1)));

        TemplateDef snap = sel.dataSnap;
        cachedIslands = TemplateGateUtils.computeGateIslands(snap);

        refreshGateList();
        refreshLinkList();

        // choices = all other gates across map
        var others = enumerateAllGateRefsExcept(sel.pid).stream()
            .map(this::displayForGateRef)
            .sorted().collect(Collectors.toList());
        gateSearch.getItems().setAll(others);

        addLinkBtn.setDisable(false); removeLinkBtn.setDisable(false);
        saveGateNameBtn.setDisable(false); saveLinkNameBtn.setDisable(false);

        gateList.getSelectionModel().selectedIndexProperty().addListener((o,ov,nv)->{
            if (nv == null || nv.intValue() < 0) { gateNameField.clear(); return; }
            var ref = new Plane2DMap.GateRef(sel.pid, nv.intValue());
            var meta = map.findGateMeta(ref).orElse(null);
            gateNameField.setText(meta == null ? "" : meta.name);
        });
    }

    /* ---------------- UI builders ---------------- */

    private Node buildHeader() {
        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(4);
        int r=0;
        gp.add(new Label("Template Id:"),0,r); gp.add(lblTid,1,r++);
        gp.add(new Label("Region:"),0,r);      gp.add(lblRegion,1,r++);
        gp.add(new Label("Placement:"),0,r);   gp.add(lblPos,1,r++);
        TitledPane tp = new TitledPane("Instance", gp); tp.setCollapsible(false);
        return tp;
    }

    private Node buildInstanceRow() {
        // line 1: layer combo; line 2: rotate buttons
        VBox box = new VBox(6);
        HBox row1 = new HBox(8, new Label("Layer:"), layerCombo);
        HBox row2 = new HBox(8, rotCwBtn, rotCcwBtn);
        TitledPane tp = new TitledPane("Rendering & Transform", new VBox(6, row1, row2));
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildLayerManager() {
        layerList.setPrefHeight(120);
        HBox buttons = new HBox(8, addLayerBtn, removeLayerBtn);
        VBox box = new VBox(6, layerList, buttons);
        TitledPane tp = new TitledPane("Layers (Plane)", box);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildGates() {
        gateList.setPrefHeight(150);
        HBox nameRow = new HBox(6, new Label("Name:"), gateNameField, saveGateNameBtn);
        VBox box = new VBox(6, gateList, nameRow);
        TitledPane tp = new TitledPane("Gates (islands)", box);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildLinks() {
        HBox addRow = new HBox(6, new Label("Connect to:"), gateSearch, addLinkBtn);
        HBox nameRow = new HBox(6, new Label("Link name:"), linkNameField, saveLinkNameBtn);
        linkList.setPrefHeight(160);
        VBox box = new VBox(6, addRow, nameRow, linkList, removeLinkBtn);
        TitledPane tp = new TitledPane("Gate Links (global)", box);
        tp.setCollapsible(false);
        return tp;
    }

    /* ---------------- Layer management ---------------- */

    private void addLayer() {
        if (map == null) return;
        int next = map.layers.size();         // use consecutive indices
        map.layers.add(next);
        layerList.getItems().add(next);
        layerList.getSelectionModel().select(next);
        // No need to remap placement layers; new layer is at the end
        onRequestRedraw.run();
        // refresh instance combo items
        if (sel != null) refresh(sel);
    }

    private void removeSelectedLayer() {
        if (map == null) return;
        Integer selIdx = layerList.getSelectionModel().getSelectedItem();
        if (selIdx == null) return;
        if (map.layers.size() <= 1) return; // keep at least one layer

        // Remove the layer index and reindex tail
        int removed = selIdx;
        map.layers.removeIf(i -> i == removed);

        // Shift all layers > removed down by 1, and clamp items that matched removed to 0
        for (Plane2DMap.Placement p : new ArrayList<>(map.placements)) {
            if (p.layer == removed) p.layer = 0;
            else if (p.layer > removed) p.layer -= 1;
        }
        // Rebuild layer numbering [0..n-1]
        for (int i = 0; i < map.layers.size(); i++) map.layers.set(i, i);

        layerList.setItems(FXCollections.observableArrayList(map.layers));
        layerList.getSelectionModel().select(Math.min(removed, map.layers.size()-1));
        onRequestRedraw.run();
        if (sel != null) refresh(sel);
    }

    /* ---------------- Gates & links helpers ---------------- */

    private void refreshGateList() {
        if (map == null || sel == null) { gateList.getItems().setAll(); return; }
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i=0; i<cachedIslands.size(); i++) {
            var ref = new Plane2DMap.GateRef(sel.pid, i);
            var meta = map.findGateMeta(ref).orElse(null);
            String disp = (meta != null && !norm(meta.name).isBlank()) ? meta.name : ("gate #"+i);
            items.add(disp + "  (" + cachedIslands.get(i).size() + " tiles)");
        }
        gateList.setItems(items);
    }

    private void refreshLinkList() {
        linkVm.clear();
        if (map == null || sel == null) { linkList.getItems().setAll(); return; }

        List<Plane2DMap.GateRef> myRefs = new ArrayList<>();
        for (int gi=0; gi<cachedIslands.size(); gi++) myRefs.add(new Plane2DMap.GateRef(sel.pid, gi));

        for (Plane2DMap.GateLink gl : map.gateLinks) {
            for (Plane2DMap.GateRef r : myRefs) {
                if (gl.involves(r)) { linkVm.add(gl); break; }
            }
        }

        ObservableList<String> items = FXCollections.observableArrayList();
        for (Plane2DMap.GateLink gl : linkVm) {
            boolean aIsMe = (gl.a != null && gl.a.pid() != null && gl.a.pid().equals(sel.pid));
            Plane2DMap.GateRef me = aIsMe ? gl.a : gl.b;
            Plane2DMap.GateRef other = aIsMe ? gl.b : gl.a;

            String name = (gl.name == null || gl.name.isBlank()) ? "(unnamed)" : gl.name;
            String row = "["+name+"]  " + displayForGateRef(me) + "  ⇄  " + displayForGateRef(other) + "  {" + gl.id + "}";
            items.add(row);
        }
        linkList.setItems(items);
    }

    private String displayForGateRef(Plane2DMap.GateRef r) {
        if (map == null) return r.toString();
        var m = map.findGateMeta(r).orElse(null);
        return (m != null && m.name != null && !m.name.isBlank())
            ? m.name
            : (r.pid() + "#gate" + r.gateIndex());
    }

    private List<Plane2DMap.GateRef> enumerateAllGateRefsExcept(String excludePid) {
        if (map == null) return List.of();
        List<Plane2DMap.GateRef> out = new ArrayList<>();
        for (Plane2DMap.Placement p : map.placements) {
            if (p.pid.equals(excludePid)) continue;
            var islands = TemplateGateUtils.computeGateIslands(p.dataSnap);
            for (int gi=0; gi<islands.size(); gi++) {
                var ref = new Plane2DMap.GateRef(p.pid, gi);
                map.ensureGateMeta(ref);
                out.add(ref);
            }
        }
        return out;
    }

    private void addLinkFromSearch() {
        if (map == null || sel == null) return;
        int myGate = gateList.getSelectionModel().getSelectedIndex();
        if (myGate < 0) { info("Select one of this instance's gates (left list) first."); return; }

        String target = norm(gateSearch.getEditor().getText());
        if (target.isBlank()) return;

        Plane2DMap.GateRef other = map.gateMetas.stream()
            .filter(m -> target.equals(m.name))
            .map(m -> m.ref)
            .findFirst()
            .orElseGet(() -> {
                if (!target.contains("#gate")) return null;
                String[] parts = target.split("#gate");
                try { return new Plane2DMap.GateRef(parts[0], Integer.parseInt(parts[1])); }
                catch (Exception ignored) { return null; }
            });

        if (other == null) return;

        Plane2DMap.GateRef me = new Plane2DMap.GateRef(sel.pid, myGate);
        boolean exists = map.gateLinks.stream().anyMatch(gl ->
            (gl.a.equals(me) && gl.b.equals(other)) || (gl.a.equals(other) && gl.b.equals(me)));
        if (!exists) {
            String nm = norm(linkNameField.getText());
            map.gateLinks.add(new Plane2DMap.GateLink(me, other, nm));
        }
        refreshLinkList();
    }

    private void removeSelectedLink() {
        if (map == null || sel == null) return;
        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            Plane2DMap.GateLink gl = linkVm.get(idx);
            map.gateLinks.remove(gl);
        } else {
            int myGate = gateList.getSelectionModel().getSelectedIndex();
            if (myGate >= 0) {
                Plane2DMap.GateRef me = new Plane2DMap.GateRef(sel.pid, myGate);
                map.gateLinks.removeIf(gl -> gl.involves(me));
            }
        }
        refreshLinkList();
    }

    private void renameSelectedLink() {
        if (map == null) return;
        String newName = norm(linkNameField.getText());
        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            linkVm.get(idx).name = newName;
        } else {
            int myGate = gateList.getSelectionModel().getSelectedIndex();
            if (sel != null && myGate >= 0) {
                Plane2DMap.GateRef me = new Plane2DMap.GateRef(sel.pid, myGate);
                map.gateLinks.stream().filter(gl -> gl.involves(me)).forEach(gl -> gl.name = newName);
            }
        }
        refreshLinkList();
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg); a.setHeaderText(null); a.showAndWait();
    }

    private static String norm(String s){ return s==null ? "" : s.trim(); }
}
