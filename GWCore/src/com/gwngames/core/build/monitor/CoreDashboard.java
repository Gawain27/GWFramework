package com.gwngames.core.build.monitor;

import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.base.cfg.IClassLoader;
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
import io.javalin.Javalin;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Init(module = ModuleNames.CORE)
public class CoreDashboard extends BaseComponent implements IDashboard, AutoCloseable {
    FileLogger log = FileLogger.get(LogFiles.MONITOR);
    @Inject
    private IConfig config;
    @Inject
    private IAssetManager assetManager;
    @Inject
    private IClassLoader loader;


    private final AtomicReference<Javalin> serverRef = new AtomicReference<>();
    private volatile Integer boundPort = null;
    private volatile Thread shutdownHook = null;

    @PostInject
    private void postInject() {
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
            .get("/dashboard", ctx -> ctx.result(renderBoard()))
            .start(port);

        serverRef.set(s);
        boundPort = port;
        installShutdownHook();
    }

    @Override
    public InputStream renderBoard(){
        // TODO
        return null;
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
