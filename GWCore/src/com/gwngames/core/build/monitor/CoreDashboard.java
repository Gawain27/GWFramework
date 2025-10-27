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
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.cfg.BuildParameters;
import com.gwngames.core.util.StringUtils;
import io.javalin.Javalin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Init(module = ModuleNames.CORE)
public class CoreDashboard extends BaseComponent implements IDashboard, AutoCloseable {
    private final FileLogger log = FileLogger.get(LogFiles.MONITOR);

    @Inject
    private IConfig config;
    @Inject
    private IAssetManager assetManager;

    /**
     * All dashboard content blocks (boxes)
     */
    @Inject(loadAll = true)
    private List<IDashboardContent<? extends IDashboardItem<?>>> contents;

    private final AtomicReference<Javalin> serverRef = new AtomicReference<>();
    private volatile Integer boundPort = null;
    private volatile Thread shutdownHook = null;

    @PostInject
    private void postInject() {
        // nothing yet
    }

    public void maybeStart() {
        Integer port = config.get(BuildParameters.DASHBOARD_PORT);
        if (port == null) {
            log.debug("Dashboard port not configured; skipping start.");
            return;
        }
        maybeStart(port);
    }

    public synchronized void maybeStart(int port) {
        Boolean isProd = config.get(BuildParameters.PROD_ENV);
        if (Boolean.TRUE.equals(isProd)) {
            log.debug("Dashboard disabled in production mode.");
            return;
        }
        ensureServerOn(port);
    }

    @Override
    public synchronized void shutdown() {
        stopServer();
    }

    @Override
    public void close() {
        shutdown();
    }

    private void ensureServerOn(int port) {
        Javalin current = serverRef.get();
        if (current != null && Objects.equals(boundPort, port)) return;
        if (current != null) stopServer();
        startServer(port);
    }

    private void startServer(int port) {
        log.info("Starting dashboard on port {}", port);
        Javalin s = Javalin.create(cfg -> cfg.http.defaultContentType = "text/html")
            .get("/", ctx -> ctx.result(renderBoard()))
            .get("/dashboard", ctx -> ctx.result(renderBoard()))
            .start(port);

        serverRef.set(s);
        boundPort = port;
        installShutdownHook();
    }

    @Override
    public InputStream renderBoard() {
        // Prepare each content block before sorting/rendering
        for (IDashboardContent<? extends IDashboardItem<?>> c : contents) {
            try {
                c.render();
            } catch (Throwable t) {
                log.error("Error rendering content {}", c.getClass().getSimpleName(), t);
            }
        }

        String html = buildHtml();
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }

    private String buildHtml() {
        FileHandle CSSFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
        String CSS = CSSFile.readString();
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>GW Dashboard</title>
                  <style>
                """)
            .append(CSS)
            .append("""
                  </style>
                </head>
                <body>
                  <header>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M4 4h7v7H4V4zm9 0h7v7h-7V4zM4 13h7v7H4v-7zm9 7v-7h7v7h-7z" stroke="currentColor" stroke-width="1.5" />
                    </svg>
                    <h1>GW Dashboard</h1>
                    <span class="muted">lightweight · live</span>
                  </header>
                  <main class="board">
                """);

        // First, the initial empty box placeholder for future "self" dashboard
        sb.append("""
            <section class="box empty">
              <div>Dashboard Overview — coming soon</div>
            </section>
            """);

        // Render each injected content block as a box
        for (IDashboardContent<? extends IDashboardItem<?>> c : contents) {
            String title = c.getClass().getSimpleName();

            sb.append("<section class=\"box\">");
            sb.append("<h2>").append(StringUtils.escapeHtml(title)).append("</h2>");

            sb.append("<div>").append(c.render()).append("</div>");

            sb.append("</section>");
        }

        sb.append("""
              </main>
              <footer>© GW Framework · Monitor</footer>
            </body>
            </html>
            """);
        return sb.toString();
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
            try {
                stopServer();
            } catch (Throwable t) {
                log.error("Dashboard shutdown hook error", t);
            }
        }, "DashboardShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
