package com.gwngames.core.build.monitor;

import com.badlogic.gdx.files.FileHandle;
import com.gwngames.assets.css.GwcoreCssAssets;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.base.monitor.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.cfg.BuildParameters;
import com.gwngames.core.data.monitor.template.DashboardCategoryTemplate;
import com.gwngames.core.data.monitor.template.DashboardItemCategoryTemplate;
import com.gwngames.core.data.monitor.template.DashboardTableTemplate;
import com.gwngames.core.data.monitor.template.DashboardTemplateRegistry;
import io.javalin.Javalin;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Init(module = ModuleNames.CORE)
public final class CoreDashboard extends BaseComponent implements IDashboard, AutoCloseable {

    @Inject private IConfig config;
    @Inject private IAssetManager assetManager;
    @Inject(loadAll = true) private List<IDashboardItem> injectedItems;
    @Inject(loadAll = true) private List<IDashboardLayer> injectedLayers;

    // Scopes
    private enum SysTable      { SYSTEM }
    private enum TelemetryCat  { CPU, RAM, IO }
    private enum TelemetryICat { DEFAULT }

    // Template tree
    private final Map<Enum<?>, DashboardTableTemplate<?, ?>> tables = new HashMap<>();

    // Server lifecycle
    private final AtomicReference<Javalin> serverRef = new AtomicReference<>();
    private volatile Integer boundPort = null;
    private volatile Thread shutdownHook = null;

    @PostInject
    private void postInject() {
        // Built-in items defined by SUBCOMPONENT (not by concrete class)
        registerBuiltin("graph-line", SubComponentNames.DASHBOARD_CPU_CONTENT, TelemetryCat.CPU);
        registerBuiltin("kv",         SubComponentNames.DASHBOARD_RAM_CONTENT, TelemetryCat.RAM); // if your enum is RAM_CONTENT, use that exact name
        registerBuiltin("kv",         SubComponentNames.DASHBOARD_IO_CONTENT,  TelemetryCat.IO);

        // User-supplied items
        if (injectedItems != null) {
            for (IDashboardItem it : injectedItems) register(it);
        }
    }

    /** Start using configured port if not prod. */
    public void maybeStart() {
        Integer port = config.get(BuildParameters.DASHBOARD_PORT);
        if (port == null) {
            log.debug("Dashboard port not configured; skipping start.");
            return;
        }
        maybeStart(port);
    }

    /** Start (or restart if port changed) when not in prod. */
    public synchronized void maybeStart(int port) {
        Boolean isProd = config.get(BuildParameters.PROD_ENV);
        if (Boolean.TRUE.equals(isProd)) {
            log.debug("Dashboard disabled in production mode.");
            return;
        }
        ensureServerOn(port);
    }

    /** Public shutdown API. */
    public synchronized void shutdown() { stopServer(); }

    @Override public void close() { shutdown(); }

    // ───────────────────────── internals ─────────────────────────

    private void ensureServerOn(int port) {
        Javalin current = serverRef.get();
        if (current != null && Objects.equals(boundPort, port)) return;
        if (current != null) stopServer();
        startServer(port);
    }

    private void startServer(int port) {
        log.info("Starting dashboard on port {}", port);
        Javalin s = Javalin.create(cfg -> cfg.http.defaultContentType = "text/html")
            .get("/dashboard", ctx -> ctx.result(render()))
            .start(port);
        serverRef.set(s);
        boundPort = port;
        installShutdownHook();
    }

    private void stopServer() {
        Javalin s = serverRef.getAndSet(null);
        if (s != null) {
            try {
                log.info("Stopping dashboard on port {}", boundPort);
                s.stop();
            } catch (Exception e) {
                log.error("Error while stopping dashboard", e);
            } finally {
                boundPort = null;
                removeShutdownHook();
            }
        }
    }

    private void installShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            try { stopServer(); } catch (Throwable t) { log.error("Dashboard shutdown hook error", t); }
        }, "DashboardShutdownHook");
        try { Runtime.getRuntime().addShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM already shutting down */ }
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook != null) {
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException ignored) { /* JVM already shutting down */ }
        }
    }

    // ───────────────────── rendering & registry ─────────────────────

    private String render() {
        FileHandle cssFile = null;
        try {
            cssFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
        } catch (Throwable t) {
            log.debug("Dashboard CSS not available – using fallback: {}", t.toString());
        }
        String css = (cssFile != null)
            ? cssFile.readString(String.valueOf(StandardCharsets.UTF_8))
            : "body{background:#0b0d10;color:#e6eef8;font-family:Inter,system-ui,Segoe UI,Roboto,Arial,sans-serif;}";

        StringBuilder sb = new StringBuilder(8192)
            .append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Dashboard</title><style>").append(css).append("</style></head><body>");

        // Free-floating layers
        if (injectedLayers != null) {
            for (IDashboardLayer l : injectedLayers) {
                sb.append("<section>");
                if (l.getName() != null) sb.append("<h2>").append(esc(l.getName())).append("</h2>");
                l.getContents().forEach(c -> renderBlock(c, sb));
                sb.append("</section>");
            }
        }

        // Templated tables
        for (DashboardTableTemplate<?, ?> t : tables.values()) {
            sb.append("<section class='tbl'>");
            for (var cat : t.allCategories()) {
                renderBlock(cat.header, sb);
                renderBlock(cat.stats,  sb);

                for (var ic : cat.allItemCategories()) {
                    sb.append("<article class='icat'>");
                    renderBlock(ic.header, sb);
                    renderBlock(ic.stats,  sb);

                    for (IDashboardItem it : ic.items()) {
                        // ★ Resolve content by SUBCOMPONENT
                        IDashboardContent content =
                            BaseComponent.getInstance(IDashboardContent.class, it.contentSubComp());

                        // Prefer item-chosen template, fallback to content’s own (if any)
                        String tpl = it.templateId();
                        if (tpl == null || tpl.isBlank()) tpl = content.templateId();

                        DashboardTemplateRegistry.render(tpl, content.model(), sb);
                    }
                    sb.append("</article>");
                }
            }
            sb.append("</section>");
        }
        return sb.append("</body></html>").toString();
    }

    private void renderBlock(Object blk, StringBuilder sb) {
        if (blk instanceof IDashboardHeader h) {
            DashboardTemplateRegistry.render(h.templateId(), h.model(), sb);
        } else if (blk instanceof IDashboardContent c) {
            DashboardTemplateRegistry.render(c.templateId(), c.model(), sb);
        }
    }

    private static String esc(Object o) {
        return Objects.toString(o, "")
            .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private void registerBuiltin(String templateId,
                                 SubComponentNames contentSubComp,
                                 TelemetryCat catKey) {
        register(new IDashboardItem() {
            @Override public Enum<?> tableKey()        { return SysTable.SYSTEM; }
            @Override public Enum<?> categoryKey()     { return catKey; }
            @Override public Enum<?> itemCategoryKey() { return TelemetryICat.DEFAULT; }
            @Override public String templateId()       { return templateId; }
            @Override public SubComponentNames contentSubComp() { return contentSubComp; }
        });
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void register(IDashboardItem it) {
        DashboardTableTemplate<?, ?> table =
            tables.computeIfAbsent(it.tableKey(), k -> new DashboardTableTemplate<>());

        DashboardCategoryTemplate<?> cat = ((DashboardTableTemplate) table).category(
            it.categoryKey(),
            orDefault(it.categoryHeader(),     DashboardDefaults.header(it.categoryKey().name())),
            orDefault(it.categoryStatistics(), DashboardDefaults.count(1)));

        DashboardItemCategoryTemplate ic = cat.itemCategory(
            it.itemCategoryKey(),
            orDefault(it.itemCategoryHeader(), DashboardDefaults.header(it.itemCategoryKey().name())),
            orDefault(it.itemCategoryStats(),  DashboardDefaults.count(1)));

        ic.addItem(it);
    }

    private static <T> T orDefault(T val, T fallback) { return val != null ? val : fallback; }

    @Override public List<IDashboardLayer> layers() {
        return injectedLayers == null ? List.of() : List.copyOf(injectedLayers);
    }
}
