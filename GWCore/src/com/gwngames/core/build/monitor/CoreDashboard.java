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
import com.gwngames.core.base.log.LogBus;
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

    private enum SysTable      { SYSTEM }
    private enum TelemetryCat  { CPU, RAM, IO }
    private enum TelemetryICat { DEFAULT }

    private final Map<Enum<?>, DashboardTableTemplate<?, ?>> tables = new HashMap<>();

    private final AtomicReference<Javalin> serverRef = new AtomicReference<>();
    private volatile Integer boundPort = null;
    private volatile Thread shutdownHook = null;

    @PostInject
    private void postInject() {
        registerBuiltin(null, SubComponentNames.DASHBOARD_CPU_CONTENT, TelemetryCat.CPU);
        registerBuiltin(null, SubComponentNames.DASHBOARD_IO_CONTENT,  TelemetryCat.IO);
        registerBuiltin(null, SubComponentNames.DASHBOARD_RAM_CONTENT, TelemetryCat.RAM);
        if (injectedItems != null) for (IDashboardItem it : injectedItems) register(it);
    }

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

    /* ───────────────────────── internals ───────────────────────── */

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

    /* ───────────────────── rendering & registry ───────────────────── */

    private String render() {
        // CSS (fall back if asset missing)
        String css = null;
        try {
            FileHandle cssFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
            if (cssFile != null) css = cssFile.readString(StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            log.debug("Dashboard CSS not available – using fallback: {}", t.toString());
        }
        if (css == null) {
            css = """
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
            .append("<title>Dashboard</title><style>").append(css).append("</style></head><body>");

        sb.append(renderRoot());
        sb.append(SCRIPT_BLOCK);
        return sb.append("</body></html>").toString();
    }

    private String renderRootOnly() { return renderRoot().toString(); }

    /** Build body inside a stable wrapper and use <details> for collapsers. */
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

        // Free-floating layers (optional)
        if (injectedLayers != null && !injectedLayers.isEmpty()) {
            for (IDashboardLayer l : injectedLayers) {
                sb.append("<section class='tbl'>");
                sb.append("<details class='cat' open>");
                DashboardTemplateRegistry.render("summary",
                    Map.of("text", l.getName() == null ? "Layer" : l.getName()), sb);
                sb.append("<div class='cat-body'>");
                l.getContents().forEach(c -> renderBlock(c, sb));
                sb.append("</div></details>");
                sb.append("</section>");
            }
        }

        // Tables (categories + item-categories)
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

                    for (IDashboardItem it : ic.items()) {
                        if (it instanceof BaseComponent bc) {
                            String instKey = bc.dashboardKey();             // instance-level (wrappers)
                            String clsKey  = bc.getClass().getName();       // class-level (FileLogger tap)
                            int errors = LogBus.errorCount(instKey) + LogBus.errorCount(clsKey);

                            Map<String,Object> model = Map.of(
                                "title",  bc.dashboardTitle(),
                                "key",    instKey + "," + clsKey,  // pass both
                                "errors", errors
                            );
                            DashboardTemplateRegistry.render("component-item", model, sb);
                        } else {
                            IDashboardContent content =
                                BaseComponent.getInstance(IDashboardContent.class, it.contentSubComp());
                            String tpl = (it.templateId() == null || it.templateId().isBlank())
                                ? content.templateId() : it.templateId();
                            DashboardTemplateRegistry.render(tpl, content.model(), sb);
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
        int cores  = rt.availableProcessors();

        RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        long upMs   = mx.getUptime();
        String up   = humanDuration(upMs);

        String os   = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String arch = System.getProperty("os.arch");
        String jvm  = System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version");
        long pid    = ProcessHandle.current().pid();

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
            DashboardTemplateRegistry.render("h3", h.model(), sb); // plain header when not in <details>
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
            orDefault(it.categoryStatistics(), DashboardDefaults.none()));

        DashboardItemCategoryTemplate ic = cat.itemCategory(
            it.itemCategoryKey(),
            orDefault(it.itemCategoryHeader(), DashboardDefaults.header(it.itemCategoryKey().name())),
            orDefault(it.itemCategoryStats(),  DashboardDefaults.none()));

        ic.addItem(it);
    }

    private static <T> T orDefault(T val, T fallback) { return val != null ? val : fallback; }

    @Override public List<IDashboardLayer> layers() {
        return injectedLayers == null ? List.of() : List.copyOf(injectedLayers);
    }

    /**
     * Charts + live refresh. Percent axes auto-scale to 0..100 even if series are 0..1.
     * Preserves open state for both category <details.cat> and item <details.icat>.
     * Ignores empty/non-numeric data-ymin/ymax attributes.
     */
    private static final String SCRIPT_BLOCK = """
<script>
(() => {
  const ROOT = '#dash-root';
  const PX = v => Math.round(v);

  const parseSeries = (attr) => { try { return JSON.parse(attr); } catch { return []; } };

  // Treat missing/empty/non-numeric attribute as null (so we can fall back)
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

    // Bounds: prefer explicit, else 0..100 for %, else data-driven
    let lo = (yMinAttr != null) ? yMinAttr : (isPct ? 0   : Math.min(0, ...values));
    let hi = (yMaxAttr != null) ? yMaxAttr : (isPct ? 100 : Math.max(1, ...values));
    if (!Number.isFinite(lo)) lo = 0;
    if (!Number.isFinite(hi) || hi <= lo) hi = lo + 1;

    ctx.clearRect(0,0,W,H);

    // grid + Y ticks
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

    // X ticks (seconds)
    ctx.textAlign = 'center';
    ctx.fillText('0s', padL, H-6);
    ctx.fillText((values.length/2|0)+'s', (padL+W-padR)/2, H-6);
    ctx.fillText(values.length+'s', W - padR, H-6);
    ctx.textAlign = 'left';

    // Legend
    if (label) { ctx.fillStyle = '#e7e7e7'; ctx.fillText(label, padL, padT - 6); }

    // Line
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

  // Live updates; preserve <details> open state
  async function refreshFragment() {
    try {
      const oldRoot = document.querySelector(ROOT);
      if (!oldRoot) return;

      const catOpen = Array.from(oldRoot.querySelectorAll('details.cat')).map(d => d.open);
      const icatOpenByCat = Array.from(oldRoot.querySelectorAll('details.cat')).map(cat =>
        Array.from(cat.querySelectorAll('details.icat')).map(d => d.open)
      );

      const res = await fetch('/dashboard/fragment', { cache:'no-store' });
      if (!res.ok) return;
      const html = await res.text();
      const tmp = document.createElement('div');
      tmp.innerHTML = html.trim();
      const newRoot = tmp.querySelector(ROOT);
      if (newRoot) {
        const newCats = newRoot.querySelectorAll('details.cat');
        catOpen.forEach((isOpen, i) => { if (newCats[i]) newCats[i].open = isOpen; });
        newCats.forEach((cat, i) => {
          const states = icatOpenByCat[i] || [];
          const icats  = cat.querySelectorAll('details.icat');
          states.forEach((isOpen, j) => { if (icats[j]) icats[j].open = isOpen; });
        });

        oldRoot.replaceWith(newRoot);
        hydrateAll();
      }
    } catch {}
  }

  window.addEventListener('DOMContentLoaded', () => {
    hydrateAll();
    setInterval(refreshFragment, 2000);
  });
})();
</script>
""";
}
