package com.gw.editor.ui;

import com.gw.editor.map.MapDef;
import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateGateUtils;
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
 * Properties for the currently selected template instance on the map.
 */
public class TemplateInstancePropertiesPane extends VBox {

    private final TemplateRepository repo;
    private MapDef map;
    private MapDef.Placement sel;

    private final Label lblTid = new Label("-");
    private final Label lblRegion = new Label("-");
    private final Label lblPos = new Label("-");

    private final ComboBox<Integer> layerCombo = new ComboBox<>();

    /* Gates */
    private final ListView<String> gateList = new ListView<>();
    private final TextField gateNameField = new TextField();  // editable name
    private final Button saveGateNameBtn = new Button("Save Gate Name");

    /* Links (each row = one link now) */
    private final ListView<String> linkList = new ListView<>();
    private final TextField linkNameField = new TextField();  // editable name
    private final ComboBox<String> gateSearch = new ComboBox<>();
    private final Button addLinkBtn = new Button("Add Link");
    private final Button removeLinkBtn = new Button("Remove Selected Link");
    private final Button saveLinkNameBtn = new Button("Save Link Name");

    /**
     * View-model for linkList: same order as linkList items.
     */
    private final List<MapDef.GateLink> linkVm = new ArrayList<>();

    private List<List<int[]>> cachedIslands = List.of();
    private Runnable onRequestRedraw = () -> {
    };

    public TemplateInstancePropertiesPane(TemplateRepository repo) {
        this.repo = repo;

        setSpacing(8);
        setPadding(new Insets(10));
        setFillWidth(true);

        getChildren().add(buildHeader());
        getChildren().add(buildLayerBox());
        getChildren().add(buildGates());
        getChildren().add(buildLinks());

        gateSearch.setEditable(true);

        layerCombo.setOnAction(e -> {
            if (map == null || sel == null) return;
            Integer newLayer = layerCombo.getValue();
            if (newLayer == null) return;
            if (newLayer < 0 || newLayer >= map.layers.size()) return;
            sel.layer = newLayer;
            onRequestRedraw.run();
        });

        saveGateNameBtn.setOnAction(e -> {
            int idx = gateList.getSelectionModel().getSelectedIndex();
            if (map == null || sel == null || idx < 0) return;
            var ref = new MapDef.GateRef(sel.pid, idx);
            var meta = map.ensureGateMeta(ref);
            meta.name = gateNameField.getText() == null ? "" : gateNameField.getText().trim();
            refreshLinkList(); // reflects names immediately
            refreshGateList();
        });

        addLinkBtn.setOnAction(e -> addLinkFromSearch());
        removeLinkBtn.setOnAction(e -> removeSelectedLink());
        saveLinkNameBtn.setOnAction(e -> renameSelectedLink());

        // When selecting a link, show its name in the text field
        linkList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (nv == null || nv.intValue() < 0 || nv.intValue() >= linkVm.size()) {
                linkNameField.clear();
            } else {
                var gl = linkVm.get(nv.intValue());
                linkNameField.setText(gl.name == null ? "" : gl.name);
            }
        });
    }

    public void setOnRequestRedraw(Runnable r) {
        this.onRequestRedraw = (r != null ? r : () -> {
        });
    }

    public void bindMap(MapDef map) {
        this.map = map;
        refresh(null);
    }

    public void refresh(MapDef.Placement selected) {
        this.sel = selected;

        if (sel == null || map == null) {
            lblTid.setText("-");
            lblRegion.setText("-");
            lblPos.setText("-");
            gateList.getItems().setAll();
            linkList.getItems().setAll();
            gateNameField.clear();
            gateSearch.getItems().setAll();
            linkNameField.clear();
            layerCombo.getItems().setAll();
            addLinkBtn.setDisable(true);
            removeLinkBtn.setDisable(true);
            saveGateNameBtn.setDisable(true);
            saveLinkNameBtn.setDisable(true);
            linkVm.clear();
            return;
        }

        lblTid.setText(sel.templateId);
        lblRegion.setText(sel.regionIndex < 0 ? "(whole)" : ("region " + sel.regionIndex));
        lblPos.setText("(" + sel.gx + "," + sel.gy + ") • " + sel.wTiles + "×" + sel.hTiles + " tiles");

        layerCombo.getItems().setAll(map.layers);
        layerCombo.getSelectionModel().select(Math.max(0, Math.min(sel.layer, map.layers.size() - 1)));

        TemplateDef snap = sel.dataSnap;
        cachedIslands = TemplateGateUtils.computeGateIslands(snap);

        refreshGateList();
        refreshLinkList();

        // choices = all other gates across map
        var others = enumerateAllGateRefsExcept(sel.pid).stream()
            .map(this::displayForGateRef)
            .sorted()
            .collect(Collectors.toList());
        gateSearch.getItems().setAll(others);

        addLinkBtn.setDisable(false);
        removeLinkBtn.setDisable(false);
        saveGateNameBtn.setDisable(false);
        saveLinkNameBtn.setDisable(false);

        gateList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (nv == null || nv.intValue() < 0) {
                gateNameField.clear();
                return;
            }
            var ref = new MapDef.GateRef(sel.pid, nv.intValue());
            var meta = map.findGateMeta(ref).orElse(null);
            gateNameField.setText(meta == null ? "" : meta.name);
        });
    }

    private Node buildHeader() {
        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(4);
        int r = 0;
        gp.add(new Label("Template Id:"), 0, r);
        gp.add(lblTid, 1, r++);
        gp.add(new Label("Region:"), 0, r);
        gp.add(lblRegion, 1, r++);
        gp.add(new Label("Placement:"), 0, r);
        gp.add(lblPos, 1, r++);
        TitledPane tp = new TitledPane("Instance", gp);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildLayerBox() {
        HBox row = new HBox(8, new Label("Layer:"), layerCombo);
        TitledPane tp = new TitledPane("Rendering", row);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildGates() {
        TitledPane tp = new TitledPane();
        tp.setText("Gates (islands)");
        VBox box = new VBox(6);
        gateList.setPrefHeight(150);
        HBox nameRow = new HBox(6, new Label("Name:"), gateNameField, saveGateNameBtn);
        box.getChildren().addAll(gateList, nameRow);
        tp.setContent(box);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildLinks() {
        TitledPane tp = new TitledPane();
        tp.setText("Gate Links (global)");
        VBox box = new VBox(6);

        HBox addRow = new HBox(6, new Label("Connect to:"), gateSearch, addLinkBtn);
        HBox nameRow = new HBox(6, new Label("Link name:"), linkNameField, saveLinkNameBtn);

        linkList.setPrefHeight(160);

        box.getChildren().addAll(addRow, nameRow, linkList, removeLinkBtn);
        tp.setContent(box);
        tp.setCollapsible(false);
        return tp;
    }

    private void refreshGateList() {
        if (map == null || sel == null) {
            gateList.getItems().setAll();
            return;
        }
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < cachedIslands.size(); i++) {
            var ref = new MapDef.GateRef(sel.pid, i);
            var meta = map.findGateMeta(ref).orElse(null);
            String disp = (meta != null && !meta.name.isBlank()) ? meta.name : ("gate #" + i);
            items.add(disp + "  (" + cachedIslands.get(i).size() + " tiles)");
        }
        gateList.setItems(items);
    }

    /**
     * Build a flat list of all links that involve ANY gate of this placement.
     * Each list row shows the link name prominently.
     */
    private void refreshLinkList() {
        linkVm.clear();
        if (map == null || sel == null) {
            linkList.getItems().setAll();
            return;
        }

        // Collect all GateRefs for this placement
        List<MapDef.GateRef> myRefs = new ArrayList<>();
        for (int gi = 0; gi < cachedIslands.size(); gi++) {
            myRefs.add(new MapDef.GateRef(sel.pid, gi));
        }

        // Filter links that involve any of my gates, keep order of map.gateLinks
        for (MapDef.GateLink gl : map.gateLinks) {
            for (MapDef.GateRef r : myRefs) {
                if (gl.involves(r)) {
                    linkVm.add(gl);
                    break;
                }
            }
        }

        ObservableList<String> items = FXCollections.observableArrayList();
        for (MapDef.GateLink gl : linkVm) {
            boolean aIsMe = (gl.a != null && gl.a.pid != null && gl.a.pid.equals(sel.pid));
            MapDef.GateRef me = aIsMe ? gl.a : gl.b;
            MapDef.GateRef other = aIsMe ? gl.b : gl.a;

            String name = (gl.name == null || gl.name.isBlank()) ? "(unnamed)" : gl.name;
            String row = "[" + name + "]  " + displayForGateRef(me) + "  ⇄  " + displayForGateRef(other) + "  {" + gl.id + "}";
            items.add(row);
        }

        linkList.setItems(items);
    }

    private String displayForGateRef(MapDef.GateRef r) {
        if (map == null) return r.toString();
        var m = map.findGateMeta(r).orElse(null);
        String base = (m != null && m.name != null && !m.name.isBlank())
            ? m.name : (r.pid + "#gate" + r.gateIndex);
        return base;
    }

    private List<MapDef.GateRef> enumerateAllGateRefsExcept(String excludePid) {
        if (map == null) return List.of();
        List<MapDef.GateRef> out = new ArrayList<>();
        for (MapDef.Placement p : map.placements) {
            if (p.pid.equals(excludePid)) continue;
            var islands = TemplateGateUtils.computeGateIslands(p.dataSnap);
            for (int gi = 0; gi < islands.size(); gi++) {
                var ref = new MapDef.GateRef(p.pid, gi);
                map.ensureGateMeta(ref); // ensure stable naming
                out.add(ref);
            }
        }
        return out;
    }

    private void addLinkFromSearch() {
        if (map == null || sel == null) return;
        int myGate = gateList.getSelectionModel().getSelectedIndex();
        if (myGate < 0) {
            info("Select one of this instance's gates (left list) first.");
            return;
        }

        String target = gateSearch.getEditor().getText();
        if (target == null || target.isBlank()) return;

        // Try to match by display name, then by raw pid#gateN
        MapDef.GateRef other = map.gateMetas.stream()
            .filter(m -> target.equals(m.name))
            .map(m -> m.ref)
            .findFirst()
            .orElseGet(() -> {
                if (!target.contains("#gate")) return null;
                String[] parts = target.split("#gate");
                try {
                    return new MapDef.GateRef(parts[0], Integer.parseInt(parts[1]));
                } catch (Exception ignored) {
                    return null;
                }
            });

        if (other == null) return;

        MapDef.GateRef me = new MapDef.GateRef(sel.pid, myGate);

        boolean exists = map.gateLinks.stream().anyMatch(gl ->
            (gl.a.equals(me) && gl.b.equals(other)) || (gl.a.equals(other) && gl.b.equals(me))
        );
        if (!exists) {
            String nm = linkNameField.getText() == null ? "" : linkNameField.getText().trim();
            map.gateLinks.add(new MapDef.GateLink(me, other, nm));
        }
        refreshLinkList();
    }

    private void removeSelectedLink() {
        if (map == null || sel == null) return;

        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            MapDef.GateLink gl = linkVm.get(idx);
            map.gateLinks.remove(gl);
        } else {
            // fallback: remove all links involving selected gate (legacy behavior)
            int myGate = gateList.getSelectionModel().getSelectedIndex();
            if (myGate >= 0) {
                MapDef.GateRef me = new MapDef.GateRef(sel.pid, myGate);
                map.gateLinks.removeIf(gl -> gl.involves(me));
            }
        }
        refreshLinkList();
    }

    private void renameSelectedLink() {
        if (map == null) return;
        String newName = linkNameField.getText() == null ? "" : linkNameField.getText().trim();

        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            linkVm.get(idx).name = newName;  // rename the single selected link
        } else {
            // fallback: rename all links involving selected gate
            int myGate = gateList.getSelectionModel().getSelectedIndex();
            if (sel != null && myGate >= 0) {
                MapDef.GateRef me = new MapDef.GateRef(sel.pid, myGate);
                map.gateLinks.stream().filter(gl -> gl.involves(me)).forEach(gl -> gl.name = newName);
            }
        }
        refreshLinkList();
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
