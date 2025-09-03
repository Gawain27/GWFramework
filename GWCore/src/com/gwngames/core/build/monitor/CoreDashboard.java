package com.gwngames.core.build.monitor;

import com.badlogic.gdx.files.FileHandle;
import com.gwngames.assets.css.GwcoreCssAssets;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.base.monitor.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.LogBus;
import com.gwngames.core.data.ComponentNames;
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

    @Inject
    private IConfig config;
    @Inject
    private IAssetManager assetManager;
    @Inject
    private IClassLoader loader;
    @Inject(loadAll = true)
    private List<IDashboardItem> injectedItems;

    private enum SysTable      { SYSTEM }
    private enum TelemetryCat  { CPU, RAM, IO }
    private enum TelemetryICat { DEFAULT }

    private final Map<Enum<?>, DashboardTableTemplate<?, ?>> tables = new HashMap<>();

    private final AtomicReference<Javalin> serverRef = new AtomicReference<>();
    private volatile Integer boundPort = null;
    private volatile Thread shutdownHook = null;

    @PostInject
    private void postInject() {
        // Built-ins
        registerBuiltin(SubComponentNames.DASHBOARD_CPU_CONTENT, TelemetryCat.CPU);
        registerBuiltin(SubComponentNames.DASHBOARD_IO_CONTENT,  TelemetryCat.IO);
        registerBuiltin(SubComponentNames.DASHBOARD_RAM_CONTENT, TelemetryCat.RAM);

        // Items provided by DI
        if (injectedItems != null) for (IDashboardItem it : injectedItems) register(it);

        // Ensure ALL components registered in the ClassLoader are visible
        try {
            for (ComponentNames compName : ComponentNames.values()) {
                for (Object o : loader.tryCreateAll(compName)) {
                    if (o instanceof IDashboardItem it) register(it);
                }
            }
        } catch (Throwable t) {
            log.debug("ClassLoader enumeration failed: {}", t.toString());
        }

        // Also register every already-instantiated (cached) component.
        try {
            for (var live : BaseComponent.allCachedInstances()) {
                if (live instanceof IDashboardItem it) register(it);
            }
        } catch (Throwable t) {
            log.debug("BaseComponent cache enumeration failed: {}", t.toString());
        }
    }

    /* ───────────────────── server control ───────────────────── */

    public void maybeStart() {
        Integer port = config.get(BuildParameters.DASHBOARD_PORT);
        if (port == null) { log.debug("Dashboard port not configured; skipping start."); return; }
        maybeStart(port);
    }

    public synchronized void maybeStart(int port) {
        Boolean isProd = config.get(BuildParameters.PROD_ENV);
        if (Boolean.TRUE.equals(isProd)) { log.debug("Dashboard disabled in production mode."); return; }
        ensureServerOn(port);
    }

    public synchronized void shutdown() { stopServer(); }
    @Override public void close() { shutdown(); }

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
            .get("/dashboard/fragment", ctx -> ctx.result(renderRootOnly()))
            .get("/dashboard/logs", ctx -> {
                final String keys = Objects.toString(ctx.queryParam("keys"), "");
                StringBuilder out = new StringBuilder(4096);
                if (!keys.isBlank()) {
                    for (String k : keys.split(",")) {
                        String key = k.trim();
                        if (key.isEmpty()) continue;
                        List<String> lines = LogBus.recent(key);
                        if (!lines.isEmpty()) for (String line : lines) out.append(line).append('\n');
                    }
                }
                ctx.contentType("text/plain; charset=utf-8").result(out.toString());
            })
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
        catch (IllegalStateException ignored) {}
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook != null) {
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException ignored) {}
        }
    }

    /* ───────────────────── rendering ───────────────────── */

    private static final String ESSENTIAL_CSS = """
    /* Always include: modal + small UI helpers */
    .modal{position:fixed;inset:0;background:rgba(0,0,0,.75);display:none;padding:4vh 6vw;z-index:9999}
    .modal:target{display:block}
    .modal-card{background:#101010;border-radius:.75rem;max-height:92vh;overflow:auto;padding:1rem}
    .badge{padding:.08rem .45rem;border-radius:.66rem;background:#2a2a2a;color:#e7e7e7;font-weight:600}
    .badge-err{padding:.08rem .45rem;border-radius:.66rem;background:#b00020;color:#fff;font-weight:600}
    .btn{padding:.25rem .6rem;border:1px solid #2a2a2a;border-radius:.6rem;background:#161616;color:#e7e7e7;cursor:pointer}
    .btn:hover{border-color:#00bcd4}
    """;


    private String render() {
        // Theme CSS (asset or fallback)
        String themeCss = null;
        try {
            FileHandle cssFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
            if (cssFile != null) themeCss = cssFile.readString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            log.debug("Dashboard CSS not available – using fallback: {}", t.toString());
        }
        if (themeCss == null) {
            themeCss = """
            body{background:#0b0d10;color:#e6eef8;font-family:Inter,system-ui,Segoe UI,Roboto,Arial,sans-serif;}
            .page{max-width:1280px;margin:0 auto;padding:0 1.2rem}
            section.tbl{margin-top:2.4rem}
            details.cat,details.icat{background:#101010;border-radius:.75rem;padding:1.2rem;margin-top:1.2rem;box-shadow:0 3px 8px #0008}
            details.cat>summary,details.icat>summary{list-style:none;cursor:pointer;font-size:1.05rem;color:#00bcd4;margin-bottom:.4rem}
            details.cat>summary::-webkit-details-marker,details.icat>summary::-webkit-details-marker{display:none}
            .legend{font-size:.9rem;opacity:.7;margin:.25rem 0 .35rem}
            .chart{width:100%;height:220px;display:block;border-radius:.75rem}
            .kv{display:flex;justify-content:space-between;padding:.18rem .4rem;border-radius:6px}
            .kv:hover{background:rgba(0,188,212,.12);outline:1px solid rgba(0,188,212,.25)}
            canvas[data-series]:not([data-hydrated]){background:linear-gradient(90deg,#222 25%,#2a2a2a 37%,#222 63%);background-size:400% 100%;animation:loading 2.2s infinite ease;border-radius:.75rem}
            @keyframes loading{0%{background-position:100% 0}100%{background-position:0 0}}
            """;
        }

        StringBuilder sb = new StringBuilder(16384)
            .append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Dashboard</title><style>")
            .append(themeCss)
            .append(ESSENTIAL_CSS) // ← always append essential modal CSS
            .append("</style></head><body>");

        sb.append(renderRoot());
        sb.append(SCRIPT_BLOCK);
        return sb.append("</body></html>").toString();
    }

    private String renderRootOnly() { return renderRoot().toString(); }

    private StringBuilder renderRoot() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("<div id='dash-root' class='page'>");

        // Overview
        sb.append("<section class='tbl'>");
        sb.append("<details class='cat' open data-idx='sys'>");
        DashboardTemplateRegistry.render("summary", Map.of("text", "System Overview"), sb);
        sb.append("<div class='cat-body'>");
        DashboardTemplateRegistry.render("kv", systemOverview(), sb);
        sb.append("</div></details>");
        sb.append("</section>");

        // Tables
        for (DashboardTableTemplate<?, ?> t : tables.values()) {
            sb.append("<section class='tbl'>");
            int idx = 0;
            for (var cat : t.allCategories()) {
                sb.append("<details class='cat' open data-idx='").append(idx++).append("'>");
                renderSummary(cat.header, sb);
                sb.append("<div class='cat-body'>");
                renderBlock(cat.stats, sb);

                for (var ic : cat.allItemCategories()) {
                    sb.append("<details class='icat' open>");
                    renderSummary(ic.header, sb);
                    sb.append("<div class='icat-body'>");
                    renderBlock(ic.stats, sb);

                    // Sort by error count desc, then by title
                    List<IDashboardItem> items = new ArrayList<>(ic.items());
                    items.sort((a, b) -> {
                        int ea = (a instanceof BaseComponent ba) ? ba.errorCount() : 0;
                        int eb = (b instanceof BaseComponent bb) ? bb.errorCount() : 0;
                        if (eb != ea) return Integer.compare(eb, ea);
                        String na = (a instanceof BaseComponent ba) ? ba.dashboardTitle() : a.getClass().getSimpleName();
                        String nb = (b instanceof BaseComponent bb) ? bb.dashboardTitle() : b.getClass().getSimpleName();
                        return na.compareToIgnoreCase(nb);
                    });

                    for (IDashboardItem it : items) {
                        IDashboardContent content = it.itemContent();
                        if (content == null) continue;
                        try {
                            DashboardTemplateRegistry.render(content.templateId(), content.model(), sb);
                        } catch (Throwable th) {
                            String title = (it instanceof BaseComponent bc) ? bc.dashboardTitle() : it.getClass().getSimpleName();
                            sb.append("<div class='kv'><span class='k'>⚠️ Failed to render: ")
                                .append(DashboardTemplateRegistry.HeaderModel.class.getSimpleName()) // keep it escaped-ish
                                .append("</span><span class='v'>").append(th.getClass().getSimpleName()).append("</span></div>");
                            log.error("Dashboard render error in {}: {}", title, th.toString());
                        }
                    }

                    sb.append("</div></details>");
                }

                sb.append("</div></details>");
            }
            sb.append("</section>");
        }

        return sb.append("</div>");
    }

    private Map<String, Object> systemOverview() {
        Runtime rt = Runtime.getRuntime();
        int cores = rt.availableProcessors();

        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        long upMs = mx.getUptime();
        String up = humanDuration(upMs);

        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String arch = System.getProperty("os.arch");
        String jvm = System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version");
        long pid = ProcessHandle.current().pid();

        return Map.of(
            "CPU cores", cores,
            "Uptime", up,
            "OS", os + " (" + arch + ")",
            "PID", pid,
            "JVM", jvm
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
            DashboardTemplateRegistry.render("h3", h.model(), sb);
        } else if (blk instanceof IDashboardContent c) {
            DashboardTemplateRegistry.render(c.templateId(), c.model(), sb);
        }
    }

    private void renderSummary(Object header, StringBuilder sb) {
        if (header instanceof IDashboardHeader h) {
            DashboardTemplateRegistry.render("summary", h.model(), sb);
        } else {
            DashboardTemplateRegistry.render("summary",
                Map.of("text", header == null ? "" : header.toString()), sb);
        }
    }

    private void registerBuiltin(SubComponentNames contentSubComp, TelemetryCat catKey) {
        register(new IDashboardItem() {
            @Override public Enum<?> tableKey()        { return SysTable.SYSTEM; }
            @Override public Enum<?> categoryKey()     { return catKey; }
            @Override public Enum<?> itemCategoryKey() { return TelemetryICat.DEFAULT; }

            @Override public IDashboardHeader categoryHeader()      { return DashboardDefaults.header(catKey.name()); }
            @Override public IDashboardContent categoryStatistics() { return DashboardDefaults.none(); }
            @Override public IDashboardHeader itemCategoryHeader()  { return DashboardDefaults.header(TelemetryICat.DEFAULT.name()); }
            @Override public IDashboardContent itemCategoryStats()  { return DashboardDefaults.none(); }

            @Override public IDashboardContent itemContent() {
                // Render the telemetry block via sub-component
                return BaseComponent.getInstance(IDashboardContent.class, contentSubComp);
            }
        });
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private void register(IDashboardItem it) {
        DashboardTableTemplate<?, ?> table =
            tables.computeIfAbsent(it.tableKey(), k -> new DashboardTableTemplate<>());

        IDashboardHeader  catHdr   = Objects.requireNonNull(it.categoryHeader(),     "categoryHeader must not be null");
        IDashboardContent catStats = Objects.requireNonNull(it.categoryStatistics(), "categoryStatistics must not be null");
        IDashboardHeader  icHdr    = Objects.requireNonNull(it.itemCategoryHeader(), "itemCategoryHeader must not be null");
        IDashboardContent icStats  = Objects.requireNonNull(it.itemCategoryStats(),  "itemCategoryStats must not be null");

        DashboardCategoryTemplate<?> cat = ((DashboardTableTemplate) table).category(it.categoryKey(), catHdr, catStats);
        DashboardItemCategoryTemplate ic = cat.itemCategory(it.itemCategoryKey(), icHdr, icStats);
        ic.addItem(it);
    }

    /* ───────────────────── script ───────────────────── */
    private static final String SCRIPT_BLOCK = """
<script>
(() => {
  const ROOT = '#dash-root';
  const PX = v => Math.round(v);

  const parseSeries = (attr) => { try { return JSON.parse(attr); } catch { return []; } };
  const numAttr = (el, name) => {
    const raw = el.getAttribute(name);
    if (raw == null) return null;
    const s = String(raw).trim();
    if (!s) return null;
    const n = Number(s);
    return Number.isFinite(n) ? n : null;
  };

  function drawChart(canvas) {
    const raw   = canvas.getAttribute('data-series') || '[]';
    const label = (canvas.getAttribute('data-label') || '').toLowerCase();
    const yMinAttr = numAttr(canvas, 'data-ymin');
    const yMaxAttr = numAttr(canvas, 'data-ymax');
    const seriesRaw = parseSeries(raw);
    const maxRaw = seriesRaw.reduce((m,v) => Math.max(m, +v || 0), -Infinity);
    const isPct = label.includes('%');
    const looksFractional = maxRaw <= 1.0000001;
    const values = (isPct && looksFractional)
        ? seriesRaw.map(v => (+v || 0) * 100)
        : seriesRaw.map(v => +v || 0);

    const rect = canvas.getBoundingClientRect();
    const dpr  = window.devicePixelRatio || 1;
    const Wcss = rect.width  || 260;
    const Hcss = rect.height || 120;
    const ctx  = canvas.getContext('2d');
    if (!ctx) return;

    canvas.width  = PX(Wcss * dpr);
    canvas.height = PX(Hcss * dpr);
    ctx.setTransform(dpr,0,0,dpr,0,0);

    const W=Wcss, H=Hcss, padL=36, padR=12, padT=18, padB=24;

    let lo = (yMinAttr != null) ? yMinAttr : (isPct ? 0   : Math.min(0, ...values));
    let hi = (yMaxAttr != null) ? yMaxAttr : (isPct ? 100 : Math.max(1, ...values));
    if (!Number.isFinite(lo)) lo = 0;
    if (!Number.isFinite(hi) || hi <= lo) hi = lo + 1;

    ctx.clearRect(0,0,W,H);

    ctx.strokeStyle = '#2a2a2a';
    ctx.lineWidth = 1;
    ctx.font = '11px system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';
    ctx.fillStyle = '#8a8a8a';
    const ticksY = 4;
    for (let i=0;i<=ticksY;i++) {
      const y = padT + (H - padT - padB) * (i/ticksY);
      ctx.beginPath(); ctx.moveTo(padL, y); ctx.lineTo(W - padR, y); ctx.stroke();
      const v = hi - (hi-lo) * (i/ticksY);
      ctx.fillText(v.toFixed(0), 4, y+4);
    }

    ctx.textAlign = 'center';
    ctx.fillText('0s', padL, H-6);
    ctx.fillText((values.length/2|0)+'s', (padL+W-padR)/2, H-6);
    ctx.fillText(values.length+'s', W - padR, H-6);
    ctx.textAlign = 'left';

    if (label) { ctx.fillStyle = '#e7e7e7'; ctx.fillText(label, padL, padT - 6); }

    if (values.length) {
      ctx.strokeStyle = '#00bcd4';
      ctx.lineWidth = 2;
      ctx.beginPath();
      const innerW = W - padL - padR;
      const innerH = H - padT - padB;
      for (let i=0;i<values.length;i++) {
        const x = padL + innerW * (i / Math.max(1, values.length-1));
        const yNorm = (values[i] - lo) / (hi - lo);
        const y = padT + innerH * (1 - yNorm);
        if (i===0) ctx.moveTo(x,y); else ctx.lineTo(x,y);
      }
      ctx.stroke();
    }

    canvas.setAttribute('data-hydrated','1');
  }

  function hydrateAll() {
    const root = document.querySelector(ROOT);
    if (!root) return;
    requestAnimationFrame(() => {
      root.querySelectorAll('canvas.chart[data-series]:not([data-hydrated])').forEach(drawChart);
      root.querySelectorAll('canvas[data-series]:not([data-hydrated]):not(.chart)').forEach(drawChart);
    });
  }

  async function loadLogs(modalEl) {
    if (!modalEl) return;
    const keys = modalEl.getAttribute('data-keys');
    const pre  = modalEl.querySelector('pre');
    if (!keys || !pre) return;
    try {
      const res = await fetch('/dashboard/logs?keys=' + encodeURIComponent(keys), { cache:'no-store' });
      if (res.ok) pre.textContent = await res.text();
    } catch {}
  }

  function showModalFromHash() {
    if (!location.hash) return;
    const root = document.querySelector(ROOT);
    if (!root) return;
    const modal = root.querySelector(location.hash);
    if (modal && modal.classList.contains('modal')) loadLogs(modal);
  }

  window.addEventListener('DOMContentLoaded', () => {
    hydrateAll();
    showModalFromHash();
  });
  window.addEventListener('hashchange', showModalFromHash);
})();
</script>
""";
}
