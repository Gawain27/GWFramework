package com.gwngames.core.build.monitor;

import com.badlogic.gdx.files.FileHandle;
import com.gwngames.assets.css.GwcoreCssAssets;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.base.monitor.IDashboard;
import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;
import com.gwngames.core.api.base.monitor.IDashboardItem;
import com.gwngames.core.api.base.monitor.IDashboardLayer;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.cfg.BuildParameters;
import com.gwngames.core.data.monitor.content.CpuContent;
import com.gwngames.core.data.monitor.content.IoContent;
import com.gwngames.core.data.monitor.content.RamContent;
import com.gwngames.core.data.monitor.template.DashboardCategoryTemplate;
import com.gwngames.core.data.monitor.template.DashboardItemCategoryTemplate;
import com.gwngames.core.data.monitor.template.DashboardTableTemplate;
import com.gwngames.core.data.monitor.template.DashboardTemplateRegistry;
import io.javalin.Javalin;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Init(module = ModuleNames.CORE)
public final class CoreDashboard extends BaseComponent implements IDashboard {
    @Inject
    private IConfig config;
    @Inject
    private IAssetManager assetManager;

    @Inject(loadAll = true)
    private List<IDashboardItem> injectedItems;

    @Inject(loadAll = true)
    private List<IDashboardLayer> injectedLayers;

    // Simple scoping enums for the built-in telemetry
    private enum SysTable      { SYSTEM }
    private enum TelemetryCat  { CPU, RAM, IO }
    private enum TelemetryICat { DEFAULT }

    // Template tree, built once during post-injection
    private final Map<Enum<?>, DashboardTableTemplate<?, ?>> tables = new HashMap<>();

    private volatile Javalin server;

    @PostInject
    private void postInject() {
        // Built-in items
        registerBuiltin("graph-line", CpuContent.class, TelemetryCat.CPU);
        registerBuiltin("kv",         RamContent.class, TelemetryCat.RAM);
        registerBuiltin("kv",         IoContent.class,  TelemetryCat.IO);

        // User-supplied items (may be empty)
        if (injectedItems != null) {
            for (IDashboardItem it : injectedItems) {
                register(it);
            }
        }
    }

    /** Start using configured port if not prod. */
    public void maybeStart() {
        Integer port = config.get(BuildParameters.DASHBOARD_PORT);
        if (port == null){
            log.debug("No port for the dashboard defined");
            return;
        }
        maybeStart(port);
    }

    /** Start on a specific port if not prod. */
    public void maybeStart(int port) {
        Boolean isProd = config.get(BuildParameters.PROD_ENV);
        if (Boolean.TRUE.equals(isProd)) {
            log.debug("Dashboard disabled in production mode.");
            return;
        }
        startServer(port);
    }

    private synchronized void startServer(int port) {
        if (server != null) {
            log.info("Dashboard already running on port {}", port);
            return;
        }
        log.info("Starting dashboard on port {}", port);
        server = Javalin.create(cfg -> cfg.http.defaultContentType = "text/html")
            .get("/dashboard", ctx -> ctx.result(render()))
            .start(port);
    }

    private String render() {
        FileHandle cssFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
        String css = cssFile != null
            ? cssFile.readString(StandardCharsets.UTF_8.name())
            : "body{background:#0b0d10;color:#e6eef8;font-family:Inter,system-ui,Segoe UI,Roboto,Arial,sans-serif;}";

        StringBuilder sb = new StringBuilder(8192)
            .append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Dashboard</title><style>")
            .append(css)
            .append("</style></head><body>");

        // Free-floating layers
        if (injectedLayers != null) {
            for (IDashboardLayer l : injectedLayers) {
                sb.append("<section>");
                if (l.getName() != null) {
                    sb.append("<h2>").append(esc(l.getName())).append("</h2>");
                }
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
                        IDashboardContent cnt = BaseComponent.getInstance(it.model());
                        DashboardTemplateRegistry.render(cnt.templateId(), cnt.model(), sb);
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
                                 Class<? extends IDashboardContent> contentClass,
                                 TelemetryCat catKey) {
        register(new IDashboardItem() {
            @Override public Enum<?> tableKey()        { return SysTable.SYSTEM; }
            @Override public Enum<?> categoryKey()     { return catKey; }
            @Override public Enum<?> itemCategoryKey() { return TelemetryICat.DEFAULT; }
            @Override public String templateId() { return templateId; }
            @Override public Class<? extends IDashboardContent> model() { return contentClass; }
        });
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private void register(IDashboardItem it) {
        // table
        DashboardTableTemplate<?, ?> table =
            tables.computeIfAbsent(it.tableKey(), k -> new DashboardTableTemplate<>());

        // category
        DashboardCategoryTemplate<?> cat = ((DashboardTableTemplate) table).category(
            it.categoryKey(),
            orDefault(it.categoryHeader(),     DashboardDefaults.header(it.categoryKey().name())),
            orDefault(it.categoryStatistics(), DashboardDefaults.count(1)));

        // item-category
        DashboardItemCategoryTemplate ic = cat.itemCategory(
            it.itemCategoryKey(),
            orDefault(it.itemCategoryHeader(), DashboardDefaults.header(it.itemCategoryKey().name())),
            orDefault(it.itemCategoryStats(),  DashboardDefaults.count(1)));

        // finally the item
        ic.addItem(it);
    }

    private static <T> T orDefault(T val, T fallback) {
        return val != null ? val : fallback;
    }

    // IDashboard
    @Override
    public List<IDashboardLayer> layers() {
        return injectedLayers == null ? List.of() : List.copyOf(injectedLayers);
    }
}
