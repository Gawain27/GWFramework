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
        registerDefaults();
    }

    private DashboardTemplateRegistry() { }

    /* ========================== Public API ========================== */

    public static void register(String id, Renderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("templateId must be non-blank");
        }
        REG.put(id, renderer);
    }

    public static void unregister(String id) {
        REG.remove(id);
    }

    public static void render(String id, Object model, StringBuilder sb) {
        Renderer r = REG.get(id);
        if (r == null) {
            sb.append("<!-- unknown template id=")
                .append(esc(id))
                .append(" model=")
                .append(esc(model))
                .append(" -->");
            return;
        }
        r.apply(model, sb);
    }

    /* ====================== Built-in Templates ====================== */

    private static void registerDefaults() {
        // Header: expects { "text": String } or falls back to model.toString()
        register("header", (m, sb) -> {
            Object text = (m instanceof Map<?,?> map) ? get(map, "text", "") : m;
            return sb.append("<h3 class='hdr'>").append(esc(text)).append("</h3>");
        });

        // Count: expects { "value": Number } (alias of "number")
        register("count", DashboardTemplateRegistry::renderNumberLike);
        register("number", DashboardTemplateRegistry::renderNumberLike);

        // Key/Value: either {key,value} one-liner OR render all entries of a map
        register("kv", (m, sb) -> {
            if (m instanceof Map<?,?> map) {
                boolean hasPair = map.containsKey("key") || map.containsKey("value");
                if (hasPair) {
                    Object k = get(map, "key", "");
                    Object v = get(map, "value", "");
                    return kvRow(sb, k, v);
                }
                // Generic map → render all entries
                for (Map.Entry<?,?> e : map.entrySet()) {
                    kvRow(sb, e.getKey(), e.getValue());
                }
                return sb;
            }
            // Fallback: print raw model
            return kvRow(sb, null, m);
        });

        // Simple inline sparkline placeholder: model is serialized into data attribute
        register("graph-line", (m, sb) ->
            sb.append("<canvas data-series='").append(esc(m)).append("'></canvas>"));
    }

    /* ========================= Renderers ============================ */

    private static StringBuilder renderNumberLike(Object m, StringBuilder sb) {
        Object v = (m instanceof Map<?,?> map) ? get(map, "value", m) : m;
        return sb.append("<span class='num'>").append(esc(v)).append("</span>");
    }

    private static StringBuilder kvRow(StringBuilder sb, Object k, Object v) {
        sb.append("<div class='kv'>");
        if (k != null && !Objects.toString(k, "").isBlank()) {
            sb.append("<span class='k'>").append(esc(k)).append("</span>");
        }
        sb.append("<span class='v'>").append(esc(v)).append("</span></div>");
        return sb;
    }

    /* ========================== Helpers ============================= */

    /** Safe getter for wildcarded maps (avoids getOrDefault capture issue). */
    private static Object get(Map<?,?> map, String key, Object defVal) {
        Object v = map.get(key);
        return (v != null) ? v : defVal;
    }

    private static String esc(Object o) {
        return Objects.toString(o, "")
            .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
