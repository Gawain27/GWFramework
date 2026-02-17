package com.gwngames.core.build.monitor;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.base.monitor.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.cfg.BuildParameters;
import io.javalin.Javalin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Init(module = CoreModule.CORE)
public class CoreDashboard extends BaseComponent implements IDashboard, AutoCloseable {
    private final FileLogger log = FileLogger.get(LogFiles.MONITOR);

    @Inject
    private IConfig config;

    /**
     * All dashboard content blocks (boxes)
     */
    @Inject(loadAll = true)
    private List<IDashboardContent<?>> contents;

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
                log.error("Error rendering content " + c.getClass().getSimpleName(), t);
            }
        }

        String html = buildHtml();
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String prepareCss(){
        throw new IllegalStateException("No dashboard CSS defined for the project");
    }

    private String buildHtml() {

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
            .append(prepareCss())
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

        // ── Row 1: Overview (single full row) ─────────────────────────────
        sb.append("""
        <div class="board-row" style="display:flex; gap:16px; width:100%; margin-bottom:16px;">
          <section class="box" style="flex:1 1 0%;">
            <div class="empty">
              <div>Dashboard Overview — coming soon</div>
            </div>
          </section>
        </div>
        """);

        log.debug("contents found: " + contents.size());

        // ── Next rows: contents laid out as rows (N per row) ───────────────
        final int perRow = 3; // ← change this to 2/3/4 as you like
        for (int i = 0; i < contents.size(); i += perRow) {
            sb.append("<div class=\"board-row\" style=\"display:flex; gap:16px; width:100%; margin-bottom:16px;\">");

            for (int j = i; j < Math.min(i + perRow, contents.size()); j++) {
                IDashboardContent<?> c = contents.get(j);

                sb.append("<section class=\"box\" style=\"flex:1 1 0%;\">");

                // header block (use whatever your content exposes)
                try {
                    sb.append("<div>").append(c.renderHeader()).append("</div>");
                } catch (Throwable t) {
                    // If some content doesn't implement header yet, ignore
                }

                // body
                try {
                    sb.append("<div>").append(c.render()).append("</div>");
                } catch (Throwable t) {
                    log.error("Error rendering content body " + c.getClass().getSimpleName(), t);
                }

                sb.append("</section>");
            }

            sb.append("</div>"); // end row
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
