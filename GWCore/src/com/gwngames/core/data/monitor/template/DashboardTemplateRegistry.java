package com.gwngames.core.data.monitor.template;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * <h2>{@code TemplateRegistry}</h2>
 *
 * <p>A thread-safe, ultra-light registry that maps a <em>logical</em>
 * {@code templateId} to a small server-side renderer:</p>
 *
 * <pre>{@code
 *   BiFunction<Object, StringBuilder, StringBuilder>
 * }</pre>
 *
 * <p>The lambda receives the model object and a {@link StringBuilder}
 * (to avoid temporary string allocations) and must append the HTML fragment
 * directly to that builder.</p>
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // register once at bootstrap
 * TemplateRegistry.register("spark-line",
 *     (model, sb) -> sb.append("<canvas data-p='").append(model).append("'></canvas>"));
 *
 * // later in the renderer:
 * TemplateRegistry.render("spark-line", someModel, sb);
 * }</pre>
 *
 * <p>The class ships with three default templates: <kbd>number</kbd>,
 * <kbd>kv</kbd> (key/value list) and <kbd>graph-line</kbd>.</p>
 */
public final class DashboardTemplateRegistry {

    /** Functional alias for readability. */
    @FunctionalInterface
    public interface Renderer extends BiFunction<Object, StringBuilder, StringBuilder> {}

    /** Internal store – fast concurrent look-ups, infrequent writes. */
    private static final Map<String, Renderer> REG = new ConcurrentHashMap<>();

    /* ────────────────────────── static defaults ──────────────────────── */
    static {
        register("number", (m, sb) ->
            sb.append("<span class='num'>").append(esc(m)).append("</span>"));

        register("kv", (m, sb) -> {
            ((Map<?, ?>) m).forEach((k, v) ->
                sb.append("<div>").append(esc(k)).append(": ")
                    .append(esc(v)).append("</div>"));
            return sb;
        });

        register("graph-line", (m, sb) ->
            sb.append("<canvas data-series='").append(esc(m)).append("'></canvas>"));
    }

    private DashboardTemplateRegistry() { }   // utility class – no instances

    /* ───────────────────────── public API ────────────────────────────── */

    /**
     * Registers (or replaces) a template renderer.
     *
     * @param id       non-blank identifier
     * @param renderer lambda that appends HTML to the provided builder
     */
    public static void register(String id, Renderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("templateId must be non-blank");
        REG.put(id, renderer);
    }

    /** Removes a template; harmless no-op if not present. */
    public static void unregister(String id) { REG.remove(id); }

    /**
     * Convenience helper used by the dashboard renderer.
     *
     * @param id    template identifier
     * @param model payload object expected by the template
     * @param sb    destination builder
     */
    public static void render(String id, Object model, StringBuilder sb) {
        REG.getOrDefault(id, DashboardTemplateRegistry::unknown).apply(model, sb);
    }

    /* ────────────────────────── helpers ─────────────────────────────── */

    private static StringBuilder unknown(Object m, StringBuilder sb) {
        return sb.append("<!-- unknown template ").append(esc(m)).append(" -->");
    }
    private static String esc(Object o) {
        return Objects.toString(o)
            .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
