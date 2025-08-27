package com.gwngames.core.data.monitor.template;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * DashboardTemplateRegistry v2
 * - Typed models for each built-in template
 * - Gentle coercion from legacy Map/List payloads
 * - Centralized escaping
 */
public final class DashboardTemplateRegistry {

    /* ===================== typed models ===================== */

    public record HeaderModel(String text) { public HeaderModel { text = Objects.toString(text, ""); } }
    public record CountModel(Number value) { public CountModel { value = (value == null) ? 0 : value; } }

    /** Simple key/value list */
    public record KvModel(Map<String, Object> map) {
        public KvModel { map = (map == null) ? Map.of() : Map.copyOf(map); }
    }

    /** Single line series with optional label and y-bounds */
    public record GraphModel(List<Double> series, String label, Double ymin, Double ymax) {
        public GraphModel { series = (series == null) ? List.of() : List.copyOf(series); }
        public static GraphModel of(List<Double> s) { return new GraphModel(s, "", null, null); }
    }

    /** KV + one graph */
    public record PanelKvLineModel(KvModel kv, GraphModel graph) { }

    /** KV + two graphs */
    public record PanelKvDualLineModel(KvModel kv, GraphModel a, GraphModel b) { }

    /* ===================== renderer registry ===================== */

    @FunctionalInterface
    public interface Renderer<T> extends BiConsumer<T, StringBuilder> {}
    private record Entry(Class<?> type, Renderer<?> renderer) {}

    private static final Map<String, Entry> REG = new ConcurrentHashMap<>();

    /** Register a typed renderer */
    public static <T> void register(String id, Class<T> type, Renderer<T> r) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("templateId must be non-blank");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(r, "renderer");
        REG.put(id, new Entry(type, r));
    }

    /** Remove a template */
    public static void unregister(String id) { REG.remove(id); }

    /** Render using id + arbitrary model (coercion applies for built-ins) */
    @SuppressWarnings("unchecked")
    public static void render(String id, Object model, StringBuilder sb) {
        Entry e = REG.get(id);
        if (e == null) { unknown(id, model, sb); return; }

        Object coerced = coerce(id, e.type, model);
        if (coerced == null) { unknown(id, model, sb); return; }

        ((Renderer<Object>) e.renderer).accept(coerced, sb);
    }

    /* ===================== built-ins ===================== */

    static {
        // no-op (accept null model)
        register("none", Object.class, (m, sb) -> {});

        // legacy alias: old "header" -> plain <h3>
        register("header", HeaderModel.class, (m, sb) ->
            sb.append("<h3 class='hdr'>").append(esc(m.text())).append("</h3>")
        );

        // <summary>…</summary> for collapsible containers (if you wrap in <details>)
        register("summary", HeaderModel.class, (m, sb) ->
            sb.append("<summary class='hdr'>").append(esc(m.text())).append("</summary>")
        );

        // <h3>…</h3> plain header
        register("h3", HeaderModel.class, (m, sb) ->
            sb.append("<h3 class='hdr'>").append(esc(m.text())).append("</h3>")
        );

        // count badge
        register("count", CountModel.class, (m, sb) ->
            sb.append("<span class='num'>").append(esc(m.value())).append("</span>")
        );

        // kv list
        register("kv", KvModel.class, (m, sb) -> {
            m.map().forEach((k, v) -> sb
                .append("<div class='kv'><span class='k'>").append(esc(k))
                .append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));
        });

        // single sparkline canvas (axes handled client-side)
        register("graph-line", GraphModel.class, (m, sb) -> {
            sb.append("<canvas class='chart' data-series='").append(esc(m.series()))
                .append("' data-label='").append(esc(m.label())).append("'")
                .append(attr("data-ymin", m.ymin()))
                .append(attr("data-ymax", m.ymax()))
                .append("></canvas>");
        });

        // KV + one graph
        register("panel-kv-line", PanelKvLineModel.class, (m, sb) -> {
            m.kv().map().forEach((k, v) -> sb.append("<div class='kv'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));
            GraphModel g = m.graph();
            sb.append("<div class='chart-wrap'><div class='legend'>").append(esc(g.label())).append("</div>");
            render("graph-line", g, sb);
            sb.append("</div>");
        });

        // KV + two graphs (grid)
        register("panel-kv-dualline", PanelKvDualLineModel.class, (m, sb) -> {
            m.kv().map().forEach((k, v) -> sb.append("<div class='kv'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));

            sb.append("<div class='grid chart-grid'>");

            sb.append("<div class='chart-wrap'><div class='legend'>").append(esc(m.a().label())).append("</div>");
            render("graph-line", m.a(), sb);
            sb.append("</div>");

            sb.append("<div class='chart-wrap'><div class='legend'>").append(esc(m.b().label())).append("</div>");
            render("graph-line", m.b(), sb);
            sb.append("</div>");

            sb.append("</div>");
        });

        // component-item  — row with error badge + Logs button
        register("component-item", Map.class, (m, sb) -> {
            String title  = Objects.toString(m.get("title"), "");
            String safeId = Objects.toString(m.get("safeId"), "");
            int errors    = Integer.parseInt(Objects.toString(m.get("errors"), "0"));
            String badgeCls = errors > 0 ? "badge-err" : "badge";

            sb.append("<div class='kv' role='group' aria-label='component'>")
                .append("<span class='k'>").append(esc(title)).append("</span>")
                .append("<span class='v'>")
                .append("<span class='").append(badgeCls).append("' title='Errors'>").append(errors).append("</span>")
                .append(" <a href='#").append(esc(safeId)).append("' class='btn'>Logs</a>")
                .append("</span></div>");
        });

        // log-popup  — hash-target overlay
        register("log-popup", Map.class, (m, sb) -> {
            String title  = Objects.toString(m.get("title"), "");
            String safeId = Objects.toString(m.get("safeId"), "");
            String keys   = Objects.toString(m.get("keys"), "");

            sb.append("<div id='").append(esc(safeId)).append("' class='modal' data-keys='").append(esc(keys)).append("'>")
                .append("<div class='modal-card'>")
                .append("<div style='display:flex;justify-content:space-between;align-items:center;'>")
                .append("<h3 style='margin:0;color:#00bcd4'>").append(esc(title)).append("</h3>")
                .append("<a href='#' style='color:#e7e7e7'>✕</a></div>")
                .append("<pre style='white-space:pre-wrap;font-family:ui-monospace,monospace;margin-top:.8rem;'></pre>")
                .append("</div></div>");
        });

        // composite
        register("component-with-logs", Map.class, (m, sb) -> {
            Object tile  = m.get("tile");
            Object popup = m.get("popup");
            render("component-item", tile, sb);
            render("log-popup", popup, sb);
        });
    }

    private DashboardTemplateRegistry() {}

    /* ===================== helpers ===================== */

    private static void unknown(String id, Object model, StringBuilder sb) {
        sb.append("<!-- unknown template id=").append(esc(id))
            .append(" model=").append(esc(model)).append(" -->");
    }

    private static String esc(Object o) {
        return Objects.toString(o, "")
            .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String attr(String name, Object v) {
        if (v == null) return "";
        String s = Objects.toString(v, "").trim();
        return s.isEmpty() ? "" : " " + name + "='" + esc(s) + "'";
    }

    /** Best-effort coercion so legacy Map/List callers keep working. */
    @SuppressWarnings("unchecked")
    private static Object coerce(String id, Class<?> target, Object model) {
        // Accept null for templates that don't care about a model (e.g., "none")
        if (target == Object.class) return model;

        if (model == null) return null;
        if (target.isInstance(model)) return model;

        // friendly coercions for built-ins
        if (target == HeaderModel.class) {
            if (model instanceof Map<?,?> m) {
                Object t = m.get("text");
                return new HeaderModel(Objects.toString(t, ""));
            }
            return new HeaderModel(Objects.toString(model, ""));
        }

        if (target == CountModel.class) {
            if (model instanceof Map<?,?> m) {
                Object v = m.get("value");
                Number n = (v instanceof Number) ? (Number) v : 0;
                return new CountModel(n);
            }
            if (model instanceof Number n) return new CountModel(n);
        }

        if (target == KvModel.class) {
            if (model instanceof Map<?,?> m) return new KvModel((Map<String,Object>) m);
        }

        if (target == GraphModel.class) {
            if (model instanceof Map<?,?> m) {
                List<Double> series = toSeries(m.get("series"));
                Object labObj = m.get("label");
                String label = labObj == null ? "" : labObj.toString();
                Double y0 = toD(m.get("ymin"));
                Double y1 = toD(m.get("ymax"));
                return new GraphModel(series, label, y0, y1);
            }
            if (model instanceof List<?> l) return GraphModel.of((List<Double>) l);
        }

        if (target == PanelKvLineModel.class && model instanceof Map<?,?> m1) {
            KvModel kv = (KvModel) coerce("kv", KvModel.class, m1.get("kv"));
            List<Double> series = toSeries(m1.get("series"));
            String label = Objects.toString(m1.get("label"), "");
            Double y0 = toD(m1.get("ymin"));
            Double y1 = toD(m1.get("ymax"));
            GraphModel g = new GraphModel(series, label, y0, y1);
            return new PanelKvLineModel(kv, g);
        }

        if (target == PanelKvDualLineModel.class && model instanceof Map<?,?> m2) {
            KvModel kv = (KvModel) coerce("kv", KvModel.class, m2.get("kv"));

            List<Double> aSeries = toSeries(m2.get("a"));
            String aLabel = Objects.toString(m2.get("alabel"), "");
            Double aY0 = toD(m2.get("a_ymin"));
            Double aY1 = toD(m2.get("a_ymax"));
            GraphModel a = new GraphModel(aSeries, aLabel, aY0, aY1);

            List<Double> bSeries = toSeries(m2.get("b"));
            String bLabel = Objects.toString(m2.get("blabel"), "");
            Double bY0 = toD(m2.get("b_ymin"));
            Double bY1 = toD(m2.get("b_ymax"));
            GraphModel b = new GraphModel(bSeries, bLabel, bY0, bY1);

            return new PanelKvDualLineModel(kv, a, b);
        }

        // unknown combo
        return null;
    }

    private static Double toD(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return (o == null) ? null : Double.valueOf(o.toString()); }
        catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<Double> toSeries(Object o) {
        if (o instanceof List<?> l) {
            ArrayList<Double> out = new ArrayList<>(l.size());
            for (Object e : l) {
                if (e instanceof Number n) out.add(n.doubleValue());
                else {
                    try { out.add(Double.valueOf(String.valueOf(e))); }
                    catch (Exception ignored) {}
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }
}
