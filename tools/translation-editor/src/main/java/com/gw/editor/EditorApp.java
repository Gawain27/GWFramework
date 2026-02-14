package com.gw.editor;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.*;

public class EditorApp extends Application {

    /* ───── config (unchanged) ───── */
    private record Cfg(
        Path ROOT, Path GR_PROPS,
        String LOCALE_KEY, String DEFAULT_LOCALE,
        String CSV_REL, String KEY_COL, int COL_W) {
        private static final Cfg INST = new Cfg(
            Paths.get(".").toAbsolutePath().normalize().getParent().getParent(),
            Paths.get(".").toAbsolutePath().normalize().getParent().getParent().resolve("gradle.properties"),
            "supportedLocales",
            "en_US",
            "resources/translation/messages.csv",
            "key",
            150);
    }

    private static Cfg C() {
        return Cfg.INST;
    }

    /* ───── logger ───── */
    private enum Lv {TRACE, DEBUG, INFO, WARN}

    private static final Lv FILTER =
        Lv.valueOf(System.getProperty("gwlog", "TRACE").toUpperCase());

    private static void log(Lv l, String m) {
        if (l.ordinal() < FILTER.ordinal()) return;
        System.out.printf("%8s | %-5s | %s%n",
            LocalTime.now().withNano(0), l, m);
    }

    /* ───── UI state ───── */
    private final TextField filterField = new TextField();
    private final ComboBox<String> moduleBox = new ComboBox<>();
    private final ComboBox<String> localeBox = new ComboBox<>();
    private final Button translateBtn = new Button("🌍 Translate");
    private final Button saveBtn = new Button("💾 Save");
    private final TableView<ObservableList<String>> table = new TableView<>();

    private List<String> header = List.of();
    private final Map<String, Path> moduleCsv = new TreeMap<>();
    private List<List<String>> snapshot = List.of();      // diff baseline

    // member fields
    private final ObservableList<ObservableList<String>> rowsRaw =
        FXCollections.observableArrayList();
    private final FilteredList<ObservableList<String>> rowsView =
        new FilteredList<>(rowsRaw, a -> true);

    // primary stage (for centering/owning popups)
    private Stage primaryStage;

    /* ───── start ───── */
    @Override
    public void start(Stage st) {
        scanProject();

        moduleBox.setPromptText("Module");
        moduleBox.getItems().setAll(moduleCsv.keySet());
        moduleBox.valueProperty().addListener((a, b, nv) -> {
            log(Lv.INFO, "Module changed → " + nv);
            loadCsv(nv);
        });

        filterField.setPromptText("🔍 Filter…");
        filterField.setPrefWidth(180);

        localeBox.setPromptText("Locale");
        localeBox.valueProperty().addListener((a, b, nv) -> {
            log(Lv.INFO, "Locale changed → " + nv);
            translateBtn.setDisable(nv == null);
        });
        translateBtn.setDisable(true);
        translateBtn.setOnAction(a -> autoTranslate());

        saveBtn.setDisable(true);
        saveBtn.setOnAction(a -> saveCsv());

        var bar = new ToolBar(
            moduleBox, localeBox, translateBtn,
            new Separator(), saveBtn,
            new Separator(), filterField
        );

        filterField.textProperty().addListener((a, b, newText) -> {
            String needle = newText.toLowerCase().trim();
            rowsView.setPredicate(row -> needle.isEmpty() ||
                row.stream().anyMatch(cell -> cell.toLowerCase().contains(needle)));
        });

        var scene = new Scene(new BorderPane(table, bar, null, null, null),
            960, 600);
        scene.getStylesheets().add(darculaCss());
        table.setPlaceholder(new Label("No module loaded"));

        st.setScene(scene);
        st.setTitle("GWFramework Translation Editor");
        st.show();
        this.primaryStage = st;            // ← store owner for popups
        log(Lv.INFO, "Editor ready");
    }

    /* ───── CSS ───── */
    private static String darculaCss() {
        String css = """
            .root{
              -fx-font-family:"Segoe UI","Helvetica Neue",sans-serif;
              -fx-font-size:13;
              -fx-background-color:#2b2b2b;
            }
            /* toolbar */
            .tool-bar{
              -fx-background-color:linear-gradient(#3d3f43 0%,#323438 100%);
              -fx-background-insets:0; -fx-padding:6;
            }
            .tool-bar .combo-box, .tool-bar .button{
              -fx-background-color:#4b4d51;
              -fx-text-fill:white; -fx-background-radius:18;
              -fx-cursor:hand; -fx-padding:4 14 4 14;
            }
            .tool-bar .combo-box:hover, .tool-bar .button:hover{
              -fx-background-color:#58606a;
            }
            .combo-box .list-cell { -fx-text-fill:white; }
            .combo-box-popup .list-view{ -fx-background-color:#000000; }
            .combo-box-popup .list-cell{ -fx-background-color:#000000; -fx-text-fill:white; }
            .combo-box-popup .list-cell:hover{ -fx-background-color:#3d3f43; }

            /* table */
            .table-view{
              -fx-background-color:#3c3f41;
              -fx-background-radius:8; -fx-padding:10;
              -fx-table-cell-border-color:transparent;
            }
            .table-view .column-header-background{
              -fx-background-color:linear-gradient(#4e5054 0%,#43464a 100%);
            }
            .table-view .column-header .label{ -fx-text-fill:white; -fx-font-weight:bold; }
            .table-view .column-header, .table-view .filler{ -fx-size:28; }
            .table-row-cell:odd   { -fx-background-color:#323437; }
            .table-row-cell:even  { -fx-background-color:#2b2b2b; }
            .table-row-cell:hover { -fx-background-color:#45494c; }
            .table-row-cell:selected{ -fx-background-color:#537bd7; }
            .table-view .cell{ -fx-text-fill:white; -fx-padding:5 9 5 9; }

            /* inline editor */
            .text-field{
              -fx-background-color:transparent; -fx-border-color:transparent;
              -fx-text-fill:white;
            }
            .text-field:focused{
              -fx-background-color:transparent;
              -fx-border-color:#ff9e44; -fx-border-width:0 0 2 0;
            }

            /* ==== scrollbars (TableView uses VirtualFlow) ===================== */
            .table-view .virtual-flow .scroll-bar:vertical,
            .table-view .virtual-flow .scroll-bar:horizontal{
              -fx-opacity: 1.0;
              -fx-background-color: #2b2b2b;
            }
            .table-view .virtual-flow .scroll-bar:vertical { -fx-pref-width: 14; }
            .table-view .virtual-flow .scroll-bar:horizontal { -fx-pref-height: 14; }

            .table-view .virtual-flow .scroll-bar .track{
              -fx-background-color: #2f3235;
              -fx-background-radius: 4;
            }
            .table-view .virtual-flow .scroll-bar .thumb{
              -fx-background-color:#62676f;
              -fx-background-insets: 0;
              -fx-background-radius:4;
            }
            .table-view .virtual-flow .scroll-bar .thumb:hover{
              -fx-background-color:#77808a;
            }

            .table-view .virtual-flow .scroll-bar .increment-button,
            .table-view .virtual-flow .scroll-bar .decrement-button{
              -fx-padding: 0;
              -fx-opacity: 0;
              -fx-shape: "";
            }
            """;
        return "data:text/css;base64," +
            java.util.Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /* ───── scan modules & locales ───── */
    private void scanProject() {
        Properties p = new Properties();
        try {
            p.load(Files.newBufferedReader(C().GR_PROPS));
        } catch (IOException ignored) {
        }
        var locs = List.of(Optional.ofNullable(p.getProperty(C().LOCALE_KEY))
            .orElse(C().DEFAULT_LOCALE).split("\\s*,\\s*"));
        header = Stream.concat(Stream.of(C().KEY_COL), locs.stream()).toList();
        log(Lv.DEBUG, "Locales = " + locs);

        try (var dir = Files.list(C().ROOT)) {
            dir.filter(Files::isDirectory).forEach(d -> {
                Path csv = d.resolve(C().CSV_REL);
                if (Files.exists(csv.getParent()))
                    moduleCsv.put(d.getFileName().toString(), csv);
            });
        } catch (IOException ignored) {
        }
        log(Lv.INFO, "Modules discovered = " + moduleCsv.keySet());
    }

    /* ───── load CSV ───── */
    private void loadCsv(String mod) {
        if (mod == null) return;
        Path csv = moduleCsv.get(mod);
        log(Lv.INFO, "Loading CSV " + csv);

        rowsRaw.clear();
        if (Files.isRegularFile(csv)) {
            try (BufferedReader br = Files.newBufferedReader(csv)) {
                String line;
                int n = 0;
                while ((line = br.readLine()) != null) {
                    var cols = List.of(line.split(",", -1));
                    if (n++ == 0) buildColumns(merge(cols));
                    else rowsRaw.add(pad(cols));
                }
            } catch (IOException ex) {
                log(Lv.WARN, "Read error " + ex);
            }
        } else buildColumns(header);

        table.setItems(rowsView);            // <— bind FilteredList
        table.requestFocus();

        saveBtn.setDisable(false);
        snapshot = rowsRaw.stream().map(ArrayList::new).collect(Collectors.toList());

        /* refresh locale drop-down */
        localeBox.getItems().setAll(header.subList(1, header.size()));
        localeBox.setValue(null);                 // reset
        translateBtn.setDisable(true);
    }

    /* header helpers */
    private List<String> merge(List<String> csvHead) {
        List<String> m = new ArrayList<>(csvHead);
        header.forEach(h -> {
            if (!m.contains(h)) m.add(h);
        });
        header = m;
        return m;
    }

    private void buildColumns(List<String> hdr) {
        table.getColumns().clear();
        header = hdr;
        for (int c = 0; c < header.size(); c++) {
            final int idx = c;
            var col = new TableColumn<ObservableList<String>, String>(header.get(c));
            col.setPrefWidth(C().COL_W);
            col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(idx)));
            col.setStyle("-fx-alignment:CENTER-LEFT;");

            if (c > 0) {
                // Popup editor cell — double-click to edit
                col.setCellFactory(a -> new TableCell<>() {
                    {
                        setOnMouseClicked(ev -> {
                            if (ev.getClickCount() == 2 && !isEmpty()) {
                                int rowIndex = getIndex();
                                ObservableList<String> row = getTableView().getItems().get(rowIndex);
                                String current = row.get(idx);
                                showCellEditor("Edit " + header.get(idx), current, newText -> {
                                    row.set(idx, newText);
                                    getTableView().refresh();
                                });
                            }
                        });
                    }

                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : item);
                        setGraphic(null);
                    }
                });
            }

            table.getColumns().add(col);
        }
        table.setEditable(false); // we use popup editor instead of inline editing
    }

    private ObservableList<String> pad(List<String> cols) {
        int miss = header.size() - cols.size();
        return FXCollections.observableArrayList(
            Stream.concat(cols.stream(), Stream.generate(() -> "").limit(miss)).toList());
    }

    /* ───── popup editor ───── */
    private void showCellEditor(String title, String initialText, java.util.function.Consumer<String> onOk) {
        Stage dlg = new Stage();
        dlg.setTitle(title);
        if (primaryStage != null) dlg.initOwner(primaryStage);
        dlg.setResizable(true);

        TextArea area = new TextArea(initialText == null ? "" : initialText);
        area.setWrapText(true);
        area.setPrefColumnCount(60);
        area.setPrefRowCount(12);

        Button okBtn = new Button("OK");
        Button cancelBtn = new Button("Cancel");
        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        okBtn.setOnAction(a -> {
            onOk.accept(area.getText());
            dlg.close();
        });
        cancelBtn.setOnAction(a -> dlg.close());

        // Close without changes on focus loss (click outside)
        dlg.focusedProperty().addListener((a, b, isNow) -> {
            if (!isNow) dlg.close();
        });

        var buttons = new ToolBar(okBtn, cancelBtn);
        var root = new BorderPane(area, null, null, buttons, null);
        Scene sc = new Scene(root, 700, 380);
        sc.getStylesheets().add(darculaCss());
        dlg.setScene(sc);

        // Center over main window
        if (primaryStage != null) {
            dlg.setX(primaryStage.getX() + (primaryStage.getWidth() - 700) / 2);
            dlg.setY(primaryStage.getY() + (primaryStage.getHeight() - 380) / 2);
        }

        dlg.show();
        area.requestFocus();
        area.positionCaret(area.getText().length());
    }

    /* ───── translation via DeepL ───── */
    private void autoTranslate() {
        String locale = localeBox.getValue();                 // e.g. de_DE
        if (locale == null) {
            alert("Choose a locale");
            return;
        }
        String deeplTarget = locale.contains("_")
            ? locale.substring(0, locale.indexOf('_')).toUpperCase()   // de_DE → DE
            : locale.toUpperCase();                                    // de → DE
        log(Lv.DEBUG, "DeepL target code = " + deeplTarget);

        int tgtIdx = header.indexOf(locale);
        int srcIdx = header.indexOf("en_US");
        if (srcIdx < 0 || tgtIdx < 0) {
            alert("Missing en_US or target column");
            return;
        }

        String apiKey = readProp("deeplApiKey");
        if (apiKey == null || apiKey.isBlank()) {
            alert("Add deeplApiKey to gradle.properties");
            return;
        }

        int filled = 0;
        for (int r = 0; r < table.getItems().size(); r++) {
            var row = table.getItems().get(r);
            String src = row.get(srcIdx), cur = row.get(tgtIdx);
            if (cur == null || cur.isBlank()) {
                log(Lv.TRACE, "Row " + r + " src=\"" + src + "\"");
                String trans = callDeepL(apiKey, src, deeplTarget);
                log(Lv.TRACE, "Row " + r + " trans=\"" + trans + "\"");
                if (trans != null) {
                    row.set(tgtIdx, trans);
                    filled++;
                }
            }
        }

        table.refresh();
        log(Lv.INFO, "Auto-translate inserted " + filled + " cells");
        alert(filled == 0 ? "All cells were already filled." :
            "Inserted " + filled + " translation" + (filled == 1 ? "" : "s") + ".");
    }

    private String callDeepL(String key, String text, String tgt) {
        try {
            // NOTE: auth_key is NO LONGER sent in the body. It must be in the Authorization header.
            String payload =
                "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                    "&target_lang=" + URLEncoder.encode(tgt.replace('_', '-'), StandardCharsets.UTF_8);

            log(Lv.DEBUG, "DeepL payload → " + payload.substring(0, Math.min(120, payload.length())));

            var url = URI.create("https://api-free.deepl.com/v2/translate").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            con.setRequestProperty("Authorization", "DeepL-Auth-Key " + key);

            try (var out = new OutputStreamWriter(con.getOutputStream(), StandardCharsets.UTF_8)) {
                out.write(payload);
            }

            int code = con.getResponseCode();
            log(Lv.DEBUG, "DeepL HTTP " + code + " (" + con.getResponseMessage() + ')');

            try (InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                log(Lv.TRACE, "DeepL body ⇐ " + body);

                if (code >= 200 && code < 300) {
                    // Response shape: {"translations":[{"detected_source_language":"EN","text":"..."}]}
                    int i = body.indexOf("\"text\":\"");
                    if (i >= 0) {
                        int j = body.indexOf('"', i + 8);
                        if (j > i) {
                            return body.substring(i + 8, j)
                                .replace("\\n", "\n")
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log(Lv.WARN, "DeepL error " + e);
        }
        return null;
    }

    /* ───── save CSV ───── */
    private void saveCsv() {
        String mod = moduleBox.getValue();
        Path csv = moduleCsv.get(mod);
        log(Lv.INFO, "Saving " + csv);
        try {
            Files.createDirectories(csv.getParent());
        } catch (IOException ignored) {
        }
        try (BufferedWriter bw = Files.newBufferedWriter(csv)) {
            bw.write(String.join(",", header));
            bw.newLine();
            for (var r : rowsRaw) bw.write(csv(r) + "\n");
        } catch (IOException e) {
            log(Lv.WARN, "Save error " + e);
        }

        int changed = 0;
        for (int i = 0; i < rowsRaw.size(); i++) {
            if (i >= snapshot.size() || !rowsRaw.get(i).equals(snapshot.get(i))) changed++;
        }
        snapshot = rowsRaw.stream().map(ArrayList::new).collect(Collectors.toList());
        alert("Saved " + rowsRaw.size() + " rows – " + changed + " updated.");
        log(Lv.INFO, "Save finished (“updated”=" + changed + ")");
    }

    /* ───── helpers ───── */
    private static String csv(List<String> r) {
        return r.stream().map(EditorApp::esc).collect(Collectors.joining(","));
    }

    private static String esc(String s) {
        return s.matches(".*[\",\\n].*") ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }

    private static void alert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private static String readProp(String k) {
        try {
            Properties p = new Properties();
            p.load(Files.newBufferedReader(C().GR_PROPS));
            return p.getProperty(k);
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] a) {
        launch(a);
    }
}
