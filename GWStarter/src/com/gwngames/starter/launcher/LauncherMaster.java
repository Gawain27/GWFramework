package com.gwngames.starter.launcher;

import com.badlogic.gdx.Application;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.base.cfg.IContext;
import com.gwngames.core.api.base.monitor.IDashboard;
import com.gwngames.core.api.build.ISystem;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.starter.Platform;
import com.gwngames.starter.StarterModule;
import com.gwngames.starter.StartupHelper;
import com.gwngames.starter.build.ILauncher;
import com.gwngames.starter.build.ILauncherMaster;

@Init(module = StarterModule.STARTER)
public class LauncherMaster extends BaseComponent implements ILauncherMaster {

    private static final Application.ApplicationType platformDetected = ILauncherMaster.detectPlatform();

    @Inject
    private IContext context;
    @Inject
    private IConfig config;
    @Inject
    private ISystem system;
    @Inject
    private IDashboard dashboard;

    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final ModuleClassLoader loader = ModuleClassLoader.getInstance();

    public void start(String[] args) {
        log.info("Starting up GWFrameWork");
        if (StartupHelper.startNewJvmIfRequired()) return; // macOS/Windows helper

        try {
            system.setup();

            log.info("Resolving new launcher...");
            ILauncher launcher = getNewLauncher();
            if (launcher == null) throw new IllegalStateException("Null launcher configured");

            // Make director visible to everyone *before* launch (the launch call blocks)
            context.put(IContext._DIRECTOR, this);
            context.put(IContext._LAUNCHER, launcher);

            // Start dashboard (disabled automatically in PROD)
            dashboard.maybeStart();

            log.info("Launching application…");
            // NOTE: On desktop this call blocks until the app exits.
            launcher.createApplication();

            // When we get here, the app has terminated → stop the dashboard.
            log.info("Application exited; shutting down dashboard…");
            dashboard.shutdown();

        } catch (Throwable t) {
            // Ensure we don’t leak the dashboard if the launch fails.
            try { dashboard.shutdown(); } catch (Throwable ignored) {}
            throw t instanceof RuntimeException re ? re : new RuntimeException(t);
        }
    }

    public static ILauncher getNewLauncher() {
        return switch (platformDetected) {
            case Android -> loader.tryCreate(CoreComponent.LAUNCHER, Platform.ANDROID);
            case iOS     -> loader.tryCreate(CoreComponent.LAUNCHER, Platform.IOS);
            case Desktop -> loader.tryCreate(CoreComponent.LAUNCHER, Platform.DESKTOP);
            case WebGL   -> loader.tryCreate(CoreComponent.LAUNCHER, Platform.WEB);
            default      -> throw new IllegalStateException("Unknown platform detected");
        };
    }
}

