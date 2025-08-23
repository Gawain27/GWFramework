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

        // header: model = { text: String }
        register("header", (m, sb) -> {
            String text = safeMap(m).getOrDefault("text", "").toString();
            return sb.append("<h3 class='hdr'>").append(esc(text)).append("</h3>");
        });

        // count badge: model = { value: Number }
        register("count", (m, sb) -> {
            Object v = safeMap(m).getOrDefault("value", "");
            return sb.append("<span class='num'>").append(esc(v)).append("</span>");
        });

        // kv list: model = Map<String, Object>
        register("kv", (m, sb) -> {
            Map<?, ?> map = asMap(m);
            map.forEach((k, v) -> sb
                .append("<div class='kv'><span class='k'>").append(esc(k))
                .append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));
            return sb;
        });

        // simple spark-line: model = [numbers...]
        register("graph-line", (m, sb) ->
            sb.append("<canvas data-series='").append(esc(m)).append("'></canvas>"));

        // panel-kv-line: model = { kv: Map<String,Object>, series: List<Double>, label?: String }
        register("panel-kv-line", (m, sb) -> {
            Map<String, Object> mm = safeMap(m);
            Map<String, Object> kv = safeMap(mm.get("kv"));

            kv.forEach((k, v) -> sb.append("<div class='kv'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));

            Object series = mm.get("series");
            Object label  = mm.getOrDefault("label", "");

            sb.append("<div style='margin-top:.6rem'>")
                .append("<div style='font-size:.85rem;opacity:.7;margin-bottom:.2rem'>")
                .append(esc(label)).append("</div>")
                .append("<canvas data-series='").append(esc(series)).append("'></canvas>")
                .append("</div>");
            return sb;
        });

        // panel-kv-dualline: model = { kv: Map<String,Object>, a: List<Double>, b: List<Double>, alabel?: String, blabel?: String }
        register("panel-kv-dualline", (m, sb) -> {
            Map<String, Object> mm = safeMap(m);
            Map<String, Object> kv = safeMap(mm.get("kv"));

            kv.forEach((k, v) -> sb.append("<div class='kv'><span class='k'>")
                .append(esc(k)).append("</span><span class='v'>").append(esc(v))
                .append("</span></div>"));

            Object a  = mm.get("a");
            Object b  = mm.get("b");
            Object la = mm.getOrDefault("alabel", "");
            Object lb = mm.getOrDefault("blabel", "");

            sb.append("<div class='grid' style='grid-template-columns:1fr 1fr;gap:0.8rem;margin-top:.6rem'>")
                .append("<div><div style='font-size:.85rem;opacity:.7;margin-bottom:.2rem'>")
                .append(esc(la)).append("</div>")
                .append("<canvas data-series='").append(esc(a)).append("'></canvas></div>")
                .append("<div><div style='font-size:.85rem;opacity:.7;margin-bottom:.2rem'>")
                .append(esc(lb)).append("</div>")
                .append("<canvas data-series='").append(esc(b)).append("'></canvas></div>")
                .append("</div>");
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
