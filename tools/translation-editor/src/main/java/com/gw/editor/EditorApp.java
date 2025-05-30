package com.gw.editor;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class EditorApp extends Application {

    private static final Path ROOT = Paths.get(".").toAbsolutePath()
        .normalize().getParent().getParent();   // ../../
    private static final Path GRADLE_PROPS = ROOT.resolve("gradle.properties");

    private final ComboBox<String> moduleBox = new ComboBox<>();
    private final TableView<ObservableList<String>> table = new TableView<>();
    private List<String> header = List.of();
    private final Map<String, Path> moduleCsv = new TreeMap<>();
    private final Button saveBtn = new Button("💾  Save");

    @Override public void start(Stage stage) throws Exception {
        scanProject();                       // fills moduleCsv & header

        moduleBox.getItems().setAll(moduleCsv.keySet());
        moduleBox.setPromptText("Choose module …");
        moduleBox.valueProperty().addListener((obs,old,val)-> loadCsv(val));

        saveBtn.setDisable(true);
        saveBtn.setOnAction(e -> saveCsv());

        ToolBar bar = new ToolBar(moduleBox, new Separator(), saveBtn);
        BorderPane root = new BorderPane(table, bar, null, null, null);
        stage.setScene(new Scene(root, 960, 600));
        stage.setTitle("GWFramework Translation Editor");
        stage.show();
    }

    /* ---------- scan modules & locales ----------------------------------- */
    private void scanProject() throws IOException {
        // 1) supportedLocales
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(GRADLE_PROPS)) { p.load(r); }
        var locales = List.of(Optional.ofNullable(p.getProperty("supportedLocales"))
            .orElse("en_US").split("\\s*,\\s*"));
        header = Stream.concat(Stream.of("key"), locales.stream()).toList();

        // 2) every child directory containing messages.csv
        try (var dirs = Files.list(ROOT)) {
            dirs.filter(Files::isDirectory).forEach(mod -> {
                Path csv = mod.resolve("resources/translations/messages.csv");
                if (Files.isRegularFile(csv)) moduleCsv.put(mod.getFileName().toString(), csv);
            });
        }
    }

    /* ---------- load one CSV into the table ------------------------------ */
    private void loadCsv(String module) {
        if (module == null) return;
        Path csv = moduleCsv.get(module);
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

        try (BufferedReader br = Files.newBufferedReader(csv)) {
            String line; boolean first=true;
            while ((line = br.readLine()) != null) {
                List<String> cols = Arrays.asList(line.split(",", -1));
                if (first) { first=false; buildColumns(cols); continue; }
                // ensure row has full width
                ObservableList<String> row = FXCollections.observableArrayList(
                    Stream.concat(cols.stream(), Stream.generate(() -> "")
                            .limit(header.size() - cols.size()))
                        .limit(header.size()).toList());
                rows.add(row);
            }
        } catch (IOException ex) { ex.printStackTrace(); }

        table.setItems(rows);
        saveBtn.setDisable(false);
    }

    /* ---------- construct editable columns -------------------------------- */
    private void buildColumns(List<String> hdr) {
        table.getColumns().clear();
        List<String> cols = Stream.concat(hdr.stream(), header.stream()).distinct().toList();
        header = cols;                               // keep in sync

        for (int i = 0; i < header.size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> c =
                new TableColumn<>(header.get(i));
            c.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().get(colIndex)));
            if (i > 0) {                             // editable locales
                c.setCellFactory(tc -> {
                    TextFieldTableCell<ObservableList<String>, String> cell =
                        new TextFieldTableCell<>();
                    cell.setAlignment(Pos.CENTER_LEFT);
                    return cell;
                });
                c.setOnEditCommit(ev ->
                    ev.getRowValue().set(colIndex, ev.getNewValue()));
            }
            c.setPrefWidth(150);
            table.getColumns().add(c);
        }
        table.setEditable(true);
    }

    /* ---------- save back to CSV ----------------------------------------- */
    private void saveCsv() {
        String module = moduleBox.getValue();
        Path csv = moduleCsv.get(module);
        try (BufferedWriter w = Files.newBufferedWriter(csv)) {
            w.write(String.join(",", header)); w.newLine();
            for (ObservableList<String> row : table.getItems()) {
                w.write(row.stream()
                    .map(s -> s.replace("\"","\"\""))      // minimal csv-escape
                    .collect(Collectors.joining(",")));
                w.newLine();
            }
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                "Saved " + (table.getItems().size()) +
                    " rows to " + module + "/messages.csv", ButtonType.OK);
            a.showAndWait();
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public static void main(String[] args) { launch(args); }
}
