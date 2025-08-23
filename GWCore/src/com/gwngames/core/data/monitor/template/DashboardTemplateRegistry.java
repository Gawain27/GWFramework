package com.gwngames.core.data.monitor.template;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class DashboardTemplateRegistry {

    @FunctionalInterface
    public interface Renderer extends BiFunction<Object, StringBuilder, StringBuilder> {}

    private static final Map<String, Renderer> REG = new ConcurrentHashMap<>();

    static {
        // no-op
        register("none", (m, sb) -> sb);

        register("header", (m, sb) -> {
            String text = safeMap(m).getOrDefault("text", "").toString();
            return sb.append("<h3 class='hdr' data-collapsible='1'>")
                .append(esc(text))
                .append("</h3>");
        });

        register("count", (m, sb) -> {
            Object v = safeMap(m).getOrDefault("value", "");
            return sb.append("<span class='num'>").append(esc(v)).append("</span>");
        });

        register("kv", (m, sb) -> {
            Map<?,?> map = asMap(m);
            map.forEach((k, v) -> sb
                .append("<div class='kv' tabindex='0'>")
                .append("<span class='k'>").append(esc(k)).append("</span>")
                .append("<span class='v'>").append(esc(v)).append("</span>")
                .append("</div>"));
            return sb;
        });

        // Single series line with optional label + y-range
        // Accepts either: model = [numbers...]  or
        // model = { series: [..], label?: "...", ymin?: num, ymax?: num }
        register("graph-line", (m, sb) -> {
            Map<String,Object> mm = safeMap(m);
            Object series = mm.isEmpty() ? m : mm.get("series");
            String label  = Objects.toString(mm.getOrDefault("label", ""), "");
            String ymin   = Objects.toString(mm.getOrDefault("ymin", ""), "");
            String ymax   = Objects.toString(mm.getOrDefault("ymax", ""), "");
            return sb.append("<canvas class='chart' ")
                .append("data-series='").append(esc(series)).append("' ")
                .append("data-label='").append(esc(label)).append("' ")
                .append("data-ymin='").append(esc(ymin)).append("' ")
                .append("data-ymax='").append(esc(ymax)).append("'></canvas>");
        });

        // KV + one sparkline
        // model = { kv: Map<String,Object>, series: [..], label?: "...", ymin?: num, ymax?: num }
        register("panel-kv-line", (m, sb) -> {
            Map<String,Object> mm = safeMap(m);
            Map<String,Object> kv = safeMap(mm.get("kv"));
            kv.forEach((k, v) -> sb.append("<div class='kv' tabindex='0'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));

            String label = Objects.toString(mm.getOrDefault("label", ""), "");
            String ymin  = Objects.toString(mm.getOrDefault("ymin", ""), "");
            String ymax  = Objects.toString(mm.getOrDefault("ymax", ""), "");

            return sb.append("<div class='chart-wrap'>")
                .append("<div class='legend'>").append(esc(label)).append("</div>")
                .append("<canvas class='chart' ")
                .append("data-series='").append(esc(mm.get("series"))).append("' ")
                .append("data-label='").append(esc(label)).append("' ")
                .append("data-ymin='").append(esc(ymin)).append("' ")
                .append("data-ymax='").append(esc(ymax)).append("'></canvas>")
                .append("</div>");
        });

        // KV + two sparklines side-by-side with labels
        // model = { kv: Map, a:[..], b:[..], alabel?: "...", blabel?: "...", ymin?: num, ymax?: num }
        register("panel-kv-dualline", (m, sb) -> {
            Map<String,Object> mm = safeMap(m);
            Map<String,Object> kv = safeMap(mm.get("kv"));
            kv.forEach((k, v) -> sb.append("<div class='kv' tabindex='0'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));

            String ymin = Objects.toString(mm.getOrDefault("ymin", ""), "");
            String ymax = Objects.toString(mm.getOrDefault("ymax", ""), "");
            String la   = Objects.toString(mm.getOrDefault("alabel", ""), "");
            String lb   = Objects.toString(mm.getOrDefault("blabel", ""), "");

            sb.append("<div class='grid chart-grid'>");
            sb.append("<div class='chart-wrap'>")
                .append("<div class='legend'>").append(esc(la)).append("</div>")
                .append("<canvas class='chart' data-series='").append(esc(mm.get("a")))
                .append("' data-label='").append(esc(la))
                .append("' data-ymin='").append(esc(ymin))
                .append("' data-ymax='").append(esc(ymax)).append("'></canvas>")
                .append("</div>");

            sb.append("<div class='chart-wrap'>")
                .append("<div class='legend'>").append(esc(lb)).append("</div>")
                .append("<canvas class='chart' data-series='").append(esc(mm.get("b")))
                .append("' data-label='").append(esc(lb))
                .append("' data-ymin='").append(esc(ymin))
                .append("' data-ymax='").append(esc(ymax)).append("'></canvas>")
                .append("</div>");

            sb.append("</div>");
            return sb;
        });
    }

    private DashboardTemplateRegistry() {}

    public static void register(String id, Renderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("templateId must be non-blank");
        REG.put(id, renderer);
    }

    public static void unregister(String id) { REG.remove(id); }

    public static void render(String id, Object model, StringBuilder sb) {
        Renderer r = REG.get(id);
        if (r == null) r = DashboardTemplateRegistry::unknown;
        r.apply(model, sb);
    }

    /* ── helpers ─────────────────────────────────────────────── */

    private static StringBuilder unknown(Object m, StringBuilder sb) {
        return sb.append("<!-- unknown template ").append(esc(m)).append(" -->");
    }

    /** Best-effort cast to Map<String,Object>, returns empty map on mismatch/null. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMap(Object m) {
        if (m instanceof Map<?, ?> mm) {
            try { return (Map<String, Object>) mm; }
            catch (ClassCastException ignored) { /* fallthrough */ }
        }
        return Map.of();
    }

    /** Lossy view for iteration where defaulting is not needed. */
    private static Map<?, ?> asMap(Object m) {
        return (m instanceof Map<?, ?> mm) ? mm : Map.of();
    }

    private static String esc(Object o) {
        return Objects.toString(o, "")
            .replace("&","&amp;")
            .replace("<","&lt;")
            .replace(">","&gt;")
            .replace("\"","&quot;");
    }
}
