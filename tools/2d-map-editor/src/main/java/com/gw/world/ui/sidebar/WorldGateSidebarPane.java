package com.gw.world.ui.sidebar;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.editor.util.TemplateGateUtils;
import com.gw.editor.util.TemplateSlice;
import com.gw.map.model.MapDef;
import com.gw.map.model.Plane2DMap;
import com.gw.world.model.WorldDef;
import com.gw.world.model.WorldDef.GateEndpoint;
import com.gw.world.model.WorldDef.GateLink;
import com.gw.world.model.WorldDef.SectionPlacement;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Right-side scrollable panel for gate management at world level:
 * - Shows gates for the currently selected section (across all planes in its map).
 * - Allows renaming gates (writes to the section's MapDef/Plane2DMap gateMetas).
 * - Allows creating/removing/renaming world-level GateLink objects between sections.
 */
public class WorldGateSidebarPane extends ScrollPane {

    private final VBox root = new VBox(8);

    private final ListView<String> gateList = new ListView<>();
    private final TextField gateNameField = new TextField();
    private final Button saveGateNameBtn = new Button("Save Gate Name");
    private final CheckBox showCollisionBox = new CheckBox("Show collision tiles");
    private final CheckBox showGateBox = new CheckBox("Show gate tiles");
    private final ListView<String> linkList = new ListView<>();
    private final TextField linkNameField = new TextField();
    private final ComboBox<String> gateSearch = new ComboBox<>();
    private final Button addLinkBtn = new Button("Add Link");
    private final Button removeLinkBtn = new Button("Remove Selected Link");
    private final Button saveLinkNameBtn = new Button("Save Link Name");

    private TemplateRepository templateRepo = new TemplateRepository();

    private WorldDef world;
    private int selectedSectionIndex = -1;

    /** Local gates for current section. */
    private final List<GateEntry> localGates = new ArrayList<>();
    /** View model for links involving local gates. */
    private final List<GateLink> linkVm = new ArrayList<>();
    /** Search index: display string -> endpoint. */
    private final Map<String, GateEndpoint> searchIndex = new HashMap<>();

    private Runnable onRequestRedraw = () -> {};

    private Consumer<WorldDef.GateEndpoint> onGateSelectionChanged = ep -> {};
    private Consumer<WorldDef.GateLink> onLinkSelectionChanged = gl -> {};

    public void setTemplateRepository(TemplateRepository repo) {
        if (repo != null) this.templateRepo = repo;
    }

    public void setOnGateSelectionChanged(Consumer<WorldDef.GateEndpoint> c) {
        this.onGateSelectionChanged = (c != null ? c : ep -> {});
    }

    public void setOnLinkSelectionChanged(Consumer<WorldDef.GateLink> c) {
        this.onLinkSelectionChanged = (c != null ? c : gl -> {});
    }

    public WorldGateSidebarPane() {
        setContent(root);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        root.setPadding(new Insets(8));
        root.setFillWidth(true);

        root.getChildren().add(buildOverlaysPane());
        root.getChildren().add(buildGatesPane());
        root.getChildren().add(buildLinksPane());

        gateSearch.setEditable(true);

        saveGateNameBtn.setOnAction(e -> saveGateName());
        addLinkBtn.setOnAction(e -> addLinkFromSearch());
        removeLinkBtn.setOnAction(e -> removeSelectedLink());
        saveLinkNameBtn.setOnAction(e -> renameSelectedLink());

        gateList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (nv == null || nv.intValue() < 0 || nv.intValue() >= localGates.size()) {
                gateNameField.clear();
                onGateSelectionChanged.accept(null);  // clear gate highlight
            } else {
                GateEntry ge = localGates.get(nv.intValue());
                Plane2DMap.GateMeta meta = findGateMeta(ge.endpoint);
                gateNameField.setText(meta == null ? "" : norm(meta.name));
                onGateSelectionChanged.accept(ge.endpoint);  // highlight this gate in blue
            }
            refreshLinkVm();
            refreshLinkList();
        });

        linkList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (nv == null || nv.intValue() < 0 || nv.intValue() >= linkVm.size()) {
                linkNameField.clear();
                onLinkSelectionChanged.accept(null);  // clear purple highlight
            } else {
                GateLink gl = linkVm.get(nv.intValue());
                linkNameField.setText(gl.name == null ? "" : gl.name);
                onLinkSelectionChanged.accept(gl);    // highlight link endpoints in purple
            }
        });

        updateControlsEnabled();
    }

    /* ------------------------------------------------------------
     *  Public API
     * ------------------------------------------------------------ */

    public void setWorld(WorldDef world) {
        this.world = world;
        if (selectedSectionIndex < 0 || !isValidSectionIndex(selectedSectionIndex)) {
            selectedSectionIndex = -1;
        }
        rebuildLocalGates();
        refreshGateList();
        refreshLinkVm();
        refreshLinkList();
        rebuildGateSearch();
        gateList.getSelectionModel().clearSelection();
        onGateSelectionChanged.accept(null);
        updateControlsEnabled();
    }

    public void setSelectedSectionIndex(int index) {
        if (index < 0 || !isValidSectionIndex(index)) {
            selectedSectionIndex = -1;
        } else {
            selectedSectionIndex = index;
        }
        rebuildLocalGates();
        refreshGateList();
        refreshLinkVm();
        refreshLinkList();
        rebuildGateSearch();
        gateList.getSelectionModel().clearSelection();
        onGateSelectionChanged.accept(null);
        updateControlsEnabled();
    }

    public void setOnRequestRedraw(Runnable r) {
        this.onRequestRedraw = (r != null ? r : () -> {});
    }

    /* ------------------------------------------------------------
     *  UI builders
     * ------------------------------------------------------------ */
    private TitledPane buildOverlaysPane() {
        VBox box = new VBox(6, showCollisionBox, showGateBox);
        TitledPane tp = new TitledPane("Overlays", box);
        tp.setCollapsible(false);
        return tp;
    }

    private TitledPane buildGatesPane() {
        gateList.setPrefHeight(200);

        HBox nameRow = new HBox(6, new Label("Name:"), gateNameField, saveGateNameBtn);
        nameRow.setFillHeight(true);

        VBox box = new VBox(6, new Label("Gates in selected section:"), gateList, nameRow);
        TitledPane tp = new TitledPane("Section Gates", box);
        tp.setCollapsible(false);
        return tp;
    }

    private TitledPane buildLinksPane() {
        linkList.setPrefHeight(220);

        HBox addRow = new HBox(6, new Label("Connect to:"), gateSearch, addLinkBtn);
        HBox nameRow = new HBox(6, new Label("Link name:"), linkNameField, saveLinkNameBtn);

        VBox box = new VBox(6,
            new Label("World links (involving selected section's gates):"),
            addRow,
            nameRow,
            linkList,
            removeLinkBtn
        );
        TitledPane tp = new TitledPane("World Gate Links", box);
        tp.setCollapsible(false);
        return tp;
    }

    /* ------------------------------------------------------------
     *  Helpers
     * ------------------------------------------------------------ */

    private boolean isValidSectionIndex(int idx) {
        return world != null && idx >= 0 && idx < world.sections.size();
    }

    private String norm(String s) {
        return s == null ? "" : s.trim();
    }

    /* ------------------------------------------------------------
     *  Local gate enumeration
     * ------------------------------------------------------------ */

    private void rebuildLocalGates() {
        localGates.clear();

        if (!isValidSectionIndex(selectedSectionIndex)) {
            return;
        }
        SectionPlacement sp = world.sections.get(selectedSectionIndex);
        MapDef map = sp.map;
        if (map == null || map.planes == null || map.planes.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Plane2DMap> e : map.planes.entrySet()) {
            String planeKey = e.getKey();
            Plane2DMap plane = e.getValue();
            if (plane == null || plane.placements.isEmpty()) continue;

            for (Plane2DMap.Placement p : plane.placements) {
                TemplateDef snap = snapshotForPlacement(p);
                if (snap == null) continue;

                var islands = TemplateGateUtils.computeGateIslands(snap);
                for (int gi = 0; gi < islands.size(); gi++) {
                    Plane2DMap.GateRef ref = new Plane2DMap.GateRef(p.pid, gi);
                    // ensure meta exists so names can be stored
                    Plane2DMap.GateMeta meta = plane.ensureGateMeta(ref);
                    GateEndpoint ep = new GateEndpoint(selectedSectionIndex, planeKey, meta.ref);
                    String disp = displayForEndpoint(ep);
                    localGates.add(new GateEntry(ep, disp));
                }
            }
        }
    }

    private void refreshGateList() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (GateEntry ge : localGates) {
            items.add(ge.display);
        }
        gateList.setItems(items);
    }

    private Plane2DMap.GateMeta findGateMeta(GateEndpoint ep) {
        if (!isValidSectionIndex(ep.sectionIndex)) return null;
        SectionPlacement sp = world.sections.get(ep.sectionIndex);
        MapDef map = sp.map;
        if (map == null) return null;

        Plane2DMap plane = map.planes.get(ep.planeKey);
        if (plane == null || ep.gateRef == null) return null;

        // ensure a meta exists for that ref
        return plane.ensureGateMeta(ep.gateRef);
    }

    private String displayForEndpoint(GateEndpoint ep) {
        if (!isValidSectionIndex(ep.sectionIndex)) {
            return "(unknown section)";
        }
        SectionPlacement sp = world.sections.get(ep.sectionIndex);
        MapDef map = sp.map;

        String sectionLabel = "Section#" + ep.sectionIndex;
        String mapName = (map != null && map.name != null && !map.name.isBlank())
            ? map.name
            : (sp.mapId != null ? sp.mapId : "(map?)");

        String planeLabel = ep.planeKey != null ? ep.planeKey : "(plane?)";
        String gateLabel;

        Plane2DMap.GateMeta meta = findGateMeta(ep);
        if (meta != null && meta.name != null && !meta.name.isBlank()) {
            gateLabel = meta.name;
        } else if (ep.gateRef != null) {
            gateLabel = ep.gateRef.toString();
        } else {
            gateLabel = "(gate?)";
        }

        return sectionLabel + " • " + mapName + " • " + planeLabel + " • " + gateLabel;
    }

    /* ------------------------------------------------------------
     *  World link VM + search
     * ------------------------------------------------------------ */

    private void refreshLinkVm() {
        linkVm.clear();
        if (world == null) return;
        if (localGates.isEmpty()) return;

        Set<String> localKeySet = localGates.stream()
            .map(ge -> endpointKey(ge.endpoint))
            .collect(Collectors.toSet());

        for (GateLink gl : world.gateLinks) {
            if (gl.a == null || gl.b == null) continue;
            String ka = endpointKey(gl.a);
            String kb = endpointKey(gl.b);
            if (localKeySet.contains(ka) || localKeySet.contains(kb)) {
                linkVm.add(gl);
            }
        }
    }

    private void refreshLinkList() {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (GateLink gl : linkVm) {
            String aStr = displayForEndpoint(gl.a);
            String bStr = displayForEndpoint(gl.b);
            String nm = (gl.name == null || gl.name.isBlank()) ? "(unnamed)" : gl.name;
            String row = "[" + nm + "]  " + aStr + "  ⇄  " + bStr + "  {" + gl.id + "}";
            items.add(row);
        }
        linkList.setItems(items);
    }

    private String endpointKey(GateEndpoint ep) {
        if (ep == null || ep.gateRef == null) return "";
        return ep.sectionIndex + "|" + ep.planeKey + "|" + ep.gateRef.pid() + "|" + ep.gateRef.gateIndex();
    }

    private TemplateDef snapshotForPlacement(Plane2DMap.Placement p) {
        if (p.dataSnap != null) return p.dataSnap;

        TemplateDef src = templateRepo.findById(p.templateId);
        if (src == null) return null;

        TemplateDef snap;
        if (p.regionIndex >= 0) {
            // Same logic as in WorldIsoRenderer / PlaneSidebarPane
            int tw = Math.max(1, src.tileWidthPx);
            int th = Math.max(1, src.tileHeightPx);
            int x0 = Math.max(0, p.srcXpx / tw);
            int y0 = Math.max(0, p.srcYpx / th);
            int x1 = Math.max(0, (p.srcXpx + p.srcWpx - 1) / tw);
            int y1 = Math.max(0, (p.srcYpx + p.srcHpx - 1) / th);
            snap = TemplateSlice.copyRegion(src, x0, y0, x1, y1);
        } else {
            snap = TemplateSlice.copyWhole(src);
        }

        p.dataSnap = snap;
        return snap;
    }

    private void rebuildGateSearch() {
        gateSearch.getItems().clear();
        searchIndex.clear();

        if (world == null || world.sections == null || world.sections.isEmpty()) return;

        List<GateEndpoint> all = new ArrayList<>();

        for (int si = 0; si < world.sections.size(); si++) {
            SectionPlacement sp = world.sections.get(si);
            MapDef map = sp.map;
            if (map == null || map.planes == null || map.planes.isEmpty()) continue;

            for (Map.Entry<String, Plane2DMap> e : map.planes.entrySet()) {
                String planeKey = e.getKey();
                Plane2DMap plane = e.getValue();
                if (plane == null || plane.placements.isEmpty()) continue;

                for (Plane2DMap.Placement p : plane.placements) {
                    TemplateDef snap = snapshotForPlacement(p);
                    if (snap == null) continue;

                    var islands = TemplateGateUtils.computeGateIslands(snap);
                    for (int gi = 0; gi < islands.size(); gi++) {
                        Plane2DMap.GateRef ref = new Plane2DMap.GateRef(p.pid, gi);
                        Plane2DMap.GateMeta meta = plane.ensureGateMeta(ref);
                        GateEndpoint ep = new GateEndpoint(si, planeKey, meta.ref);
                        all.add(ep);
                    }
                }
            }
        }

        // exclude current section's gates from the search list
        List<GateEndpoint> others = all.stream()
            .filter(ep -> ep.sectionIndex != selectedSectionIndex)
            .toList();

        List<String> labels = new ArrayList<>();
        for (GateEndpoint ep : others) {
            String label = displayForEndpoint(ep);
            labels.add(label);
            searchIndex.put(label, ep);
        }
        labels.sort(String::compareTo);
        gateSearch.getItems().addAll(labels);
    }

    /* ------------------------------------------------------------
     *  Actions
     * ------------------------------------------------------------ */

    private void saveGateName() {
        int idx = gateList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= localGates.size()) return;

        GateEntry ge = localGates.get(idx);
        Plane2DMap.GateMeta meta = findGateMeta(ge.endpoint);
        if (meta == null) return;

        meta.name = norm(gateNameField.getText());
        // refresh labels that depend on gate names
        rebuildLocalGates();
        refreshGateList();
        rebuildGateSearch();
        refreshLinkList();
    }

    private void addLinkFromSearch() {
        if (world == null) return;
        int myIdx = gateList.getSelectionModel().getSelectedIndex();
        if (myIdx < 0 || myIdx >= localGates.size()) {
            info("Select a gate in the current section first.");
            return;
        }
        GateEntry myGate = localGates.get(myIdx);

        String targetLabel = norm(gateSearch.getEditor().getText());
        if (targetLabel.isEmpty()) return;

        GateEndpoint other = searchIndex.get(targetLabel);
        if (other == null) {
            info("Unknown target gate: " + targetLabel);
            return;
        }

        GateEndpoint me = myGate.endpoint;

        // Avoid duplicates
        boolean exists = world.gateLinks.stream().anyMatch(gl ->
            (gl.a != null && gl.b != null) &&
                ((endpointKey(gl.a).equals(endpointKey(me)) && endpointKey(gl.b).equals(endpointKey(other))) ||
                    (endpointKey(gl.a).equals(endpointKey(other)) && endpointKey(gl.b).equals(endpointKey(me))))
        );
        if (!exists) {
            String nm = norm(linkNameField.getText());
            world.gateLinks.add(new GateLink(copyEndpoint(me), copyEndpoint(other), nm));
        }

        refreshLinkVm();
        refreshLinkList();
        onRequestRedraw.run();
    }

    private GateEndpoint copyEndpoint(GateEndpoint src) {
        if (src == null || src.gateRef == null) return null;
        return new GateEndpoint(
            src.sectionIndex,
            src.planeKey,
            new Plane2DMap.GateRef(src.gateRef.pid(), src.gateRef.gateIndex())
        );
    }

    private void removeSelectedLink() {
        if (world == null) return;

        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            GateLink gl = linkVm.get(idx);
            world.gateLinks.remove(gl);
        } else {
            // If no link selected but a gate is selected, remove all links involving that gate
            int myIdx = gateList.getSelectionModel().getSelectedIndex();
            if (myIdx >= 0 && myIdx < localGates.size()) {
                GateEndpoint me = localGates.get(myIdx).endpoint;
                world.gateLinks.removeIf(gl -> gl.involves(me));
            }
        }

        refreshLinkVm();
        refreshLinkList();
        onRequestRedraw.run();
    }

    private void renameSelectedLink() {
        if (world == null) return;
        String newName = norm(linkNameField.getText());

        int idx = linkList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < linkVm.size()) {
            linkVm.get(idx).name = newName;
        } else {
            // rename all links that involve the currently selected gate
            int myIdx = gateList.getSelectionModel().getSelectedIndex();
            if (myIdx >= 0 && myIdx < localGates.size()) {
                GateEndpoint me = localGates.get(myIdx).endpoint;
                world.gateLinks.stream()
                    .filter(gl -> gl.involves(me))
                    .forEach(gl -> gl.name = newName);
            }
        }

        refreshLinkList();
        onRequestRedraw.run();
    }

    private void updateControlsEnabled() {
        boolean hasSection = isValidSectionIndex(selectedSectionIndex);
        boolean hasLocalGates = hasSection && !localGates.isEmpty();

        gateList.setDisable(!hasSection);
        gateNameField.setDisable(!hasLocalGates);
        saveGateNameBtn.setDisable(!hasLocalGates);

        boolean hasWorld = world != null;
        boolean linksEnabled = hasWorld && hasLocalGates;
        gateSearch.setDisable(!linksEnabled);
        addLinkBtn.setDisable(!linksEnabled);
        removeLinkBtn.setDisable(!linksEnabled);
        saveLinkNameBtn.setDisable(!linksEnabled);
        linkList.setDisable(!linksEnabled);
        linkNameField.setDisable(!linksEnabled);
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /* ------------------------------------------------------------
     *  Small helper record
     * ------------------------------------------------------------ */

    private static final class GateEntry {
        final GateEndpoint endpoint;
        final String display;

        GateEntry(GateEndpoint endpoint, String display) {
            this.endpoint = endpoint;
            this.display = display;
        }
    }

    public BooleanProperty showCollisionOverlayProperty() {
        return showCollisionBox.selectedProperty();
    }

    public BooleanProperty showGateOverlayProperty() {
        return showGateBox.selectedProperty();
    }

    public boolean isShowCollisionOverlay() {
        return showCollisionBox.isSelected();
    }

    public boolean isShowGateOverlay() {
        return showGateBox.isSelected();
    }
}

