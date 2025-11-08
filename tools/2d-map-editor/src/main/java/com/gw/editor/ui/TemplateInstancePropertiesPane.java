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

    private final TemplateRepository repo; // still handy for thumbnails if you add them later
    private MapDef map;
    private MapDef.Placement sel;

    private final Label lblTid = new Label("-");
    private final Label lblRegion = new Label("-");
    private final Label lblPos = new Label("-");

    private final ListView<String> gateList = new ListView<>();
    private final ListView<String> linkList = new ListView<>();
    private final ComboBox<String> gateSearch = new ComboBox<>();
    private final Button addLinkBtn = new Button("Add Link");
    private final Button removeLinkBtn = new Button("Remove Selected Link");

    private List<List<int[]>> cachedIslands = List.of();

    public TemplateInstancePropertiesPane(TemplateRepository repo) {
        this.repo = repo;

        setSpacing(8);
        setPadding(new Insets(10));
        setFillWidth(true);

        getChildren().add(buildHeader());
        getChildren().add(buildGates());
        getChildren().add(buildLinks());

        gateSearch.setEditable(true);
        addLinkBtn.setOnAction(e -> addLinkFromSearch());
        removeLinkBtn.setOnAction(e -> removeSelectedLink());
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
            gateSearch.getItems().setAll();
            addLinkBtn.setDisable(true);
            removeLinkBtn.setDisable(true);
            return;
        }

        lblTid.setText(sel.templateId);
        lblRegion.setText(sel.regionIndex < 0 ? "(whole)" : ("region " + sel.regionIndex));
        lblPos.setText("(" + sel.gx + "," + sel.gy + ") • " + sel.wTiles + "×" + sel.hTiles + " tiles");

        TemplateDef snap = sel.dataSnap;
        cachedIslands = TemplateGateUtils.computeGateIslands(snap);

        var items = new ArrayList<String>();
        for (int i = 0; i < cachedIslands.size(); i++) {
            items.add("gate #" + i + "  (" + cachedIslands.get(i).size() + " tiles)");
        }
        gateList.getItems().setAll(items);

        refreshLinkList();

        // choices = all other gates across map
        var others = enumerateAllGateRefsExcept(sel.pid).stream()
            .map(r -> r.pid + "#gate" + r.gateIndex)
            .sorted()
            .collect(Collectors.toList());
        gateSearch.getItems().setAll(others);

        addLinkBtn.setDisable(false);
        removeLinkBtn.setDisable(false);
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

    private Node buildGates() {
        TitledPane tp = new TitledPane();
        tp.setText("Gates (islands)");
        VBox box = new VBox(6);
        gateList.setPrefHeight(150);
        box.getChildren().addAll(gateList);
        tp.setContent(box);
        tp.setCollapsible(false);
        return tp;
    }

    private Node buildLinks() {
        TitledPane tp = new TitledPane();
        tp.setText("Gate Links (global)");
        VBox box = new VBox(6);

        HBox addRow = new HBox(6, new Label("Connect to:"), gateSearch, addLinkBtn);
        linkList.setPrefHeight(160);

        box.getChildren().addAll(addRow, linkList, removeLinkBtn);
        tp.setContent(box);
        tp.setCollapsible(false);
        return tp;
    }

    private void refreshLinkList() {
        if (map == null || sel == null) {
            linkList.getItems().setAll();
            return;
        }
        ObservableList<String> items = FXCollections.observableArrayList();

        for (int gi = 0; gi < cachedIslands.size(); gi++) {
            MapDef.GateRef me = new MapDef.GateRef(sel.pid, gi);
            var targets = map.gateLinks.stream()
                .filter(gl -> gl.involves(me))
                .map(gl -> gl.a.equals(me) ? gl.b : gl.a)
                .map(other -> other.pid + "#gate" + other.gateIndex)
                .sorted()
                .toList();

            String head = "gate #" + gi + " -> " + (targets.isEmpty() ? "(none)" : String.join(", ", targets));
            items.add(head);
        }
        linkList.setItems(items);
    }

    private List<MapDef.GateRef> enumerateAllGateRefsExcept(String excludePid) {
        if (map == null) return List.of();
        List<MapDef.GateRef> out = new ArrayList<>();
        for (MapDef.Placement p : map.placements) {
            if (p.pid.equals(excludePid)) continue;
            var islands = TemplateGateUtils.computeGateIslands(p.dataSnap);
            for (int gi = 0; gi < islands.size(); gi++) out.add(new MapDef.GateRef(p.pid, gi));
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
        if (target == null || !target.contains("#gate")) return;

        String[] parts = target.split("#gate");
        if (parts.length != 2) return;
        String pid = parts[0];
        int gateIdx;
        try {
            gateIdx = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
            return;
        }

        MapDef.GateRef a = new MapDef.GateRef(sel.pid, myGate);
        MapDef.GateRef b = new MapDef.GateRef(pid, gateIdx);

        boolean exists = map.gateLinks.stream().anyMatch(gl ->
            (gl.a.equals(a) && gl.b.equals(b)) || (gl.a.equals(b) && gl.b.equals(a))
        );
        if (!exists) map.gateLinks.add(new MapDef.GateLink(a, b));

        refreshLinkList();
    }

    private void removeSelectedLink() {
        if (map == null || sel == null) return;
        int myGate = gateList.getSelectionModel().getSelectedIndex();
        if (myGate < 0) {
            info("Select your gate in the left list first.");
            return;
        }
        MapDef.GateRef me = new MapDef.GateRef(sel.pid, myGate);
        map.gateLinks.removeIf(gl -> gl.involves(me));
        refreshLinkList();
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
