package com.gw.map.ui;

import com.gw.editor.template.TemplateDef;
import com.gw.editor.template.TemplateRepository;
import com.gw.map.io.DefaultTextureResolver;
import com.gw.map.io.TextureResolver;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Template Gallery (left bar) with:
 * - Search + Scale spinner
 * - Cards for: simple, complex regions, animated (looping thumb)
 * - Drag-and-drop payloads carrying template id, region index, tile size, pixel rect, and scale multiplier
 * <p>
 * Dependencies: TemplateDef, TemplateRepository (your classes).
 */
public class TemplateGalleryPane extends VBox {

    // -------- Public knobs / integration points --------

    /**
     * Dragboard payload; also put as string for interop.
     */
    public static final DataFormat PAYLOAD_FORMAT = new DataFormat("application/x-template-payload");

    /**
     * Region index constants in payload.
     */
    public static final int REGION_FULL = -1;       // use full image
    public static final int REGION_ANIMATED = -2;   // use all regions as frames
    private static final double CARD_WIDTH = 260;
    private static final double THUMB_FIT_W = 240;

    // -------- Constructor --------
    private static final double THUMB_FIT_H = 180;
    private final TemplateRepository repo;

    // -------- Internal state --------
    private final List<Timeline> activeAnimations = new ArrayList<>();
    private final Spinner<Double> scaleSpinner;
    private final TextField search;
    private final TilePane grid;

    private Consumer<TemplateDef> onOpenTemplate;
    /**
     * Hook to resolve TextureDef.logicalPath to a loadable URL string.
     */
    private TextureResolver textureResolver = new DefaultTextureResolver();

    public TemplateGalleryPane() {
        this(null);
    }

    // -------- Rendering --------
    public TemplateGalleryPane(TemplateRepository repository) {
        this.repo = repository != null ? repository : new TemplateRepository();

        setSpacing(8);
        setPadding(new Insets(8));

        // Top controls
        scaleSpinner = new Spinner<>(0.25, 8.0, 1.0, 0.25);
        scaleSpinner.setEditable(true);
        search = new TextField();
        search.setPromptText("Search templates...");

        ToolBar top = new ToolBar(new Label("Scale:"), scaleSpinner, new Separator(), new Label("Search:"), search);

        // Grid
        grid = new TilePane(10, 10);
        grid.setPrefColumns(3);
        grid.setPadding(new Insets(4));
        grid.setPrefTileWidth(CARD_WIDTH + 20);
        grid.setTileAlignment(Pos.TOP_LEFT);

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);

        getChildren().addAll(top, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        // Refresh triggers
        search.textProperty().addListener((o, a, b) -> renderAll());
        sceneProperty().addListener((o, oldS, newS) -> {
            if (newS != null) renderAll();
        });
        parentProperty().addListener((o, oldP, newP) -> renderAll());
    }

    private static String safeId(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // -------- Card building --------

    /**
     * Optional: when user clicks a card's image (not drag), you can handle opening/previewing it.
     */
    public void setOnOpenTemplate(Consumer<TemplateDef> onOpenTemplate) {
        this.onOpenTemplate = onOpenTemplate;
    }

    /**
     * (Optional) Override how logicalPath resolves to an absolute URI string loadable by JavaFX Image.
     * Defaults to trying the path as-is, then relative to CWD, then as resource stream on the classpath.
     */
    public void setTextureResolver(TextureResolver resolver) {
        this.textureResolver = resolver != null ? resolver : this.textureResolver;
    }

    private void renderAll() {
        if (grid == null) return;

        // Stop any running animations before rebuilding
        activeAnimations.forEach(Timeline::stop);
        activeAnimations.clear();

        grid.getChildren().setAll();

        String q = (search.getText() == null) ? "" : search.getText().trim().toLowerCase(Locale.ROOT);

        List<TemplateDef> templates = repo.listAll();
        templates.sort(Comparator.comparing(t -> t.id == null ? "" : t.id));

        for (TemplateDef t : templates) {
            if (!q.isEmpty()) {
                String id = (t.id == null) ? "" : t.id.toLowerCase(Locale.ROOT);
                if (!id.contains(q)) continue;
            }

            Image tex = loadTexture(t.logicalPath);

            if (t.complex && t.animated && t.regions != null && !t.regions.isEmpty()) {
                // Animated card: one per template; show looping thumbnail (using regions)
                List<int[]> frames = safePixelRegions(t);
                if (!frames.isEmpty()) {
                    int[] first = frames.getFirst();
                    List<Image> thumbFrames = makeRegionThumbFrames(tex, frames, THUMB_FIT_W, THUMB_FIT_H);

                    Card card = buildCard(t, t.id + " • (animated)", "Animated", thumbFrames.isEmpty() ? makePlaceholderThumb(THUMB_FIT_W, THUMB_FIT_H) : thumbFrames.getFirst(), tex != null);

                    // Animate: cycle frames
                    if (thumbFrames.size() > 1) {
                        Timeline tl = new Timeline();
                        int N = thumbFrames.size();
                        for (int i = 0; i < N; i++) {
                            int idx = i;
                            tl.getKeyFrames().add(new KeyFrame(Duration.millis(120 * (i + 1)), e -> card.imageView.setImage(thumbFrames.get(idx))));
                        }
                        tl.setCycleCount(Timeline.INDEFINITE);
                        tl.play();
                        activeAnimations.add(tl);
                    }

                    // Drag payload: REGION_ANIMATED with first frame rect as preview
                    enableDrag(card.imageView, t, REGION_ANIMATED, first);

                    grid.getChildren().add(card.root);
                } else {
                    // Fallback to simple card if no frames
                    grid.getChildren().add(buildSimpleFallbackCard(t, tex).root);
                }

            } else if (t.complex && t.regions != null && !t.regions.isEmpty()) {
                // Complex, non-animated: one card per region
                List<int[]> prs = safePixelRegions(t);
                for (int i = 0; i < prs.size(); i++) {
                    int[] r = prs.get(i);
                    String rid = safeId(t.regions.get(i).id, "region_" + (i + 1));

                    Image thumb = makeRegionThumb(tex, r, THUMB_FIT_W, THUMB_FIT_H, false);
                    Card card = buildCard(t, t.id + " • " + rid, "Region", thumb != null ? thumb : makePlaceholderThumb(THUMB_FIT_W, THUMB_FIT_H), tex != null);

                    enableDrag(card.imageView, t, i, r);
                    grid.getChildren().add(card.root);
                }

            } else {
                // Simple
                int[] fullRect = new int[]{0, 0, Math.max(1, t.imageWidthPx), Math.max(1, t.imageHeightPx)};
                Image thumb = makeTemplateThumb(t, tex, THUMB_FIT_W, THUMB_FIT_H);
                Card card = buildCard(t, safeId(t.id, "(unnamed)"), "Simple", thumb != null ? thumb : makePlaceholderThumb(THUMB_FIT_W, THUMB_FIT_H), tex != null);

                enableDrag(card.imageView, t, REGION_FULL, fullRect);
                grid.getChildren().add(card.root);
            }
        }
    }

    // -------- Drag support --------

    private Card buildCard(TemplateDef t, String title, String type, Image thumb, boolean clickable) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(6));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(CARD_WIDTH);
        card.setStyle(
            "-fx-border-color: -fx-box-border; " +
                "-fx-background-color: -fx-control-inner-background;"
        );

        ImageView iv = new ImageView(thumb);
        iv.setPreserveRatio(true);

        // Do NOT fix the height; only clamp width if needed
        double w = thumb.getWidth();
        if (w > THUMB_FIT_W) {
            iv.setFitWidth(THUMB_FIT_W);
        }
        // otherwise, keep the natural size (no fitHeight at all)

        if (clickable) {
            iv.setCursor(Cursor.HAND);
            iv.setOnMouseClicked(e -> {
                if (onOpenTemplate != null) onOpenTemplate.accept(t);
            });
        }

        Label name = new Label(title);
        name.setWrapText(true);
        name.setMaxWidth(THUMB_FIT_W);

        Label subt = new Label(type);
        subt.setTextFill(
            "Animated".equals(type) ? Color.DARKBLUE :
                "Region".equals(type)   ? Color.DARKRED  :
                    Color.DARKGREEN
        );

        card.getChildren().addAll(iv, name, subt);
        return new Card(card, iv);
    }

    // -------- Thumbnail creation --------

    private Card buildSimpleFallbackCard(TemplateDef t, Image tex) {
        Image ph = makePlaceholderThumb(THUMB_FIT_W, THUMB_FIT_H);
        return buildCard(t, safeId(t.id, "(animated)"), "Animated", ph, tex != null);
    }

    private void enableDrag(ImageView view, TemplateDef t, int regionIndex, int[] rectPx) {
        view.setOnDragDetected(e -> {
            var db = view.startDragAndDrop(TransferMode.COPY);
            db.setDragView(view.getImage(), view.getImage().getWidth() / 2, view.getImage().getHeight() / 2);

            double scaleMul = scaleSpinner.getValue() == null ? 1.0 : scaleSpinner.getValue();

            String payload = String.join("|", safe(t.id), String.valueOf(regionIndex),                                   // -1 full, -2 animated, >=0 region idx
                String.valueOf(Math.max(1, t.tileWidthPx)), String.valueOf(Math.max(1, t.tileHeightPx)), String.valueOf(rectPx[0]), String.valueOf(rectPx[1]),         // x,y in pixels (source image space)
                String.valueOf(rectPx[2]), String.valueOf(rectPx[3]),         // w,h in pixels
                String.valueOf(scaleMul));

            ClipboardContent cc = new ClipboardContent();
            cc.put(PAYLOAD_FORMAT, payload);
            cc.putString(payload); // also as string for convenience
            db.setContent(cc);
            e.consume();
        });
    }

    private Image makeTemplateThumb(TemplateDef t, Image tex, double fitW, double fitH) {
        if (tex == null) return null;
        int w = Math.max(1, t.imageWidthPx);
        int h = Math.max(1, t.imageHeightPx);
        return cropAndFit(tex, 0, 0, w, h, fitW, fitH);
    }

    private Image makeRegionThumb(Image tex, int[] rectPx, double fitW, double fitH, boolean forAnimated) {
        if (tex == null || rectPx == null || rectPx.length < 4) return null;
        return cropAndFit(tex, rectPx[0], rectPx[1], rectPx[2], rectPx[3], fitW, fitH);
    }

    private List<Image> makeRegionThumbFrames(Image tex, List<int[]> frames, double fitW, double fitH) {
        List<Image> out = new ArrayList<>();
        if (tex == null || frames == null || frames.isEmpty()) return out;
        for (int[] r : frames) {
            Image img = makeRegionThumb(tex, r, fitW, fitH, true);
            if (img != null) out.add(img);
        }
        return out;
    }

    // -------- Helpers --------

    private Image cropAndFit(Image tex, int sx, int sy, int sw, int sh, double fitW, double fitH) {
        try {
            PixelReader pr = tex.getPixelReader();
            int maxX = (int) tex.getWidth();
            int maxY = (int) tex.getHeight();

            int cx = clamp(sx, 0, Math.max(0, maxX - 1));
            int cy = clamp(sy, 0, Math.max(0, maxY - 1));
            int cw = clamp(sw, 1, maxX - cx);
            int ch = clamp(sh, 1, maxY - cy);

            // Just crop; no fixed height, no forced scaling.
            WritableImage cropped = new WritableImage(pr, cx, cy, cw, ch);
            return cropped;
        } catch (Throwable t) {
            // Fallback placeholder if something goes wrong
            return makePlaceholderThumb(fitW, fitH);
        }
    }

    private Image makePlaceholderThumb(double fitW, double fitH) {
        Region r = new Region();
        r.setPrefSize(fitW, fitH);
        r.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(0,0,0,0.04), rgba(0,0,0,0.01)); " + "-fx-border-color: rgba(0,0,0,0.2); -fx-border-radius: 4; -fx-background-radius: 4;");
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return r.snapshot(sp, null);
    }

    private List<int[]> safePixelRegions(TemplateDef t) {
        try {
            return t.pixelRegions();
        } catch (Throwable ex) {
            return Collections.emptyList();
        }
    }

    private Image loadTexture(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return null;
        try {
            String url = textureResolver.resolve(logicalPath);
            if (url == null) return null;
            return new Image(url, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record Card(VBox root, ImageView imageView) {
    }
}
