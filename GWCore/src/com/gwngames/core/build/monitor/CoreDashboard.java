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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        // Built-ins (sub-components, not concrete classes)
        registerBuiltin(null, SubComponentNames.DASHBOARD_CPU_CONTENT, TelemetryCat.CPU);
        registerBuiltin(null, SubComponentNames.DASHBOARD_IO_CONTENT,  TelemetryCat.IO);
        registerBuiltin(null, SubComponentNames.DASHBOARD_RAM_CONTENT, TelemetryCat.RAM);
        // User-supplied items
        if (injectedItems != null) for (IDashboardItem it : injectedItems) register(it);
    }

    /** Start using configured port if not prod. */
    public void maybeStart() {
        Integer port = config.get(BuildParameters.DASHBOARD_PORT);
        if (port == null) { log.debug("Dashboard port not configured; skipping start."); return; }
        maybeStart(port);
    }

    /** Start (or restart if port changed) when not in prod. */
    public synchronized void maybeStart(int port) {
        Boolean isProd = config.get(BuildParameters.PROD_ENV);
        if (Boolean.TRUE.equals(isProd)) { log.debug("Dashboard disabled in production mode."); return; }
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
            try { log.info("Stopping dashboard on port {}", boundPort); s.stop(); }
            catch (Exception e) { log.error("Error while stopping dashboard", e); }
            finally { boundPort = null; removeShutdownHook(); }
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
        // CSS
        String css = null;
        try {
            FileHandle cssFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
            if (cssFile != null) css = cssFile.readString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            log.debug("Dashboard CSS not available – using fallback: {}", t.toString());
        }
        if (css == null) {
            css = "body{background:#0b0d10;color:#e6eef8;font-family:Inter,system-ui,Segoe UI,Roboto,Arial,sans-serif;}";
        }

        StringBuilder sb = new StringBuilder(16384)
            .append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Dashboard</title><style>")
            .append(css)
            // tiny additions for collapse + kv styling
            .append(".cat>.hdr{cursor:pointer} .cat.collapsed>.cat-body{display:none}")
            .append(".kv{display:flex;justify-content:space-between;padding:.13rem 0;font-size:.94rem;color:#8a8a8a}")
            .append("</style></head><body>");

        // ── Intro block: system overview
        sb.append("<section class='tbl'>");
        DashboardTemplateRegistry.render("header", Map.of("text", "System Overview"), sb);
        DashboardTemplateRegistry.render("kv", systemOverview(), sb);
        sb.append("</section>");

        // ── Templated tables with collapsible categories
        for (DashboardTableTemplate<?, ?> t : tables.values()) {
            sb.append("<section class='tbl'>");
            for (var cat : t.allCategories()) {
                sb.append("<div class='cat'>");
                // clickable header
                renderBlock(cat.header, sb);

                sb.append("<div class='cat-body'>");
                renderBlock(cat.stats, sb);

                for (var ic : cat.allItemCategories()) {
                    sb.append("<article class='icat'>");
                    renderBlock(ic.header, sb);
                    renderBlock(ic.stats,  sb);

                    for (IDashboardItem it : ic.items()) {
                        IDashboardContent content =
                            BaseComponent.getInstance(IDashboardContent.class, it.contentSubComp());
                        String tpl = (it.templateId() == null || it.templateId().isBlank())
                            ? content.templateId() : it.templateId();
                        DashboardTemplateRegistry.render(tpl, content.model(), sb);
                    }
                    sb.append("</article>");
                }
                sb.append("</div>"); // .cat-body
                sb.append("</div>"); // .cat
            }
            sb.append("</section>");
        }

        // ── Hydration JS: collapse + sparkline
        sb.append("<script>")
            .append(JS_TOGGLE)
            .append(JS_SPARK)
            .append("</script>");

        return sb.append("</body></html>").toString();
    }

    /** Overview info shown at the top. */
    private Map<String, Object> systemOverview() {
        Runtime rt = Runtime.getRuntime();
        int cores  = rt.availableProcessors();

        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        long upMs   = mx.getUptime();
        String up   = humanDuration(upMs);

        String os   = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String arch = System.getProperty("os.arch");
        String jvm  = System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version");
        long pid    = ProcessHandle.current().pid();

        return Map.of(
            "OS", os + " (" + arch + ")",
            "CPU cores", cores,
            "JVM", jvm,
            "PID", pid,
            "Uptime", up
        );
    }

    private static String humanDuration(long ms) {
        Duration d = Duration.ofMillis(ms);
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        long s = d.minusHours(h).minusMinutes(m).toSeconds();
        return String.format("%dh %02dm %02ds", h, m, s);
    }

    private void renderBlock(Object blk, StringBuilder sb) {
        if (blk instanceof IDashboardHeader h) {
            DashboardTemplateRegistry.render(h.templateId(), h.model(), sb);
        } else if (blk instanceof IDashboardContent c) {
            DashboardTemplateRegistry.render(c.templateId(), c.model(), sb);
        }
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
            orDefault(it.categoryStatistics(), DashboardDefaults.none()));   // ← no more “1”

        DashboardItemCategoryTemplate ic = cat.itemCategory(
            it.itemCategoryKey(),
            orDefault(it.itemCategoryHeader(), DashboardDefaults.header(it.itemCategoryKey().name())),
            orDefault(it.itemCategoryStats(),  DashboardDefaults.none()));   // ← no more “1”

        ic.addItem(it);
    }

    private static <T> T orDefault(T val, T fallback) { return val != null ? val : fallback; }

    @Override public List<IDashboardLayer> layers() {
        return injectedLayers == null ? List.of() : List.copyOf(injectedLayers);
    }

    // ── tiny inline scripts
    private static final String JS_TOGGLE =
        "document.addEventListener('click',e=>{const h=e.target.closest('.cat>.hdr');if(!h)return;h.parentElement.classList.toggle('collapsed');});";

    private static final String JS_SPARK =
        "(function(){function draw(c){try{const ctx=c.getContext('2d');const w=c.width=c.clientWidth||260;const h=c.height=48;c.setAttribute('data-hydrated','');let d=c.getAttribute('data-series');if(!d)return;let a=JSON.parse(d);if(a.length<2)a=[a[0]||0,a[0]||0];const min=Math.min.apply(null,a),max=Math.max.apply(null,a);const pad=4;ctx.clearRect(0,0,w,h);ctx.beginPath();for(let i=0;i<a.length;i++){const x=pad+(w-2*pad)*(i/(a.length-1));const y=pad+(h-2*pad)*(1-(a[i]-min)/((max-min)||1));if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.lineWidth=2;ctx.strokeStyle='rgba(0,188,212,0.9)';ctx.stroke();}catch(_){} }document.querySelectorAll('canvas[data-series]:not([data-hydrated])').forEach(draw);})();";
}
