package com.gwngames.starter.launcher;

import com.badlogic.gdx.Application;
import com.gwngames.core.api.base.IContext;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.starter.StartupHelper;
import com.gwngames.starter.build.ILauncher;
import com.gwngames.starter.build.ILauncherMaster;

@Init(module = ModuleNames.STARTER)
public class LauncherMaster extends BaseComponent implements ILauncherMaster {
    private static final Application.ApplicationType platformDetected = ILauncherMaster.detectPlatform();
    @Inject
    IContext context;
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final ModuleClassLoader loader = ModuleClassLoader.getInstance();

    public void start(String[] args) {
        log.info("Starting up GWFrameWork");
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.

        log.info("Looking up operating system type...");
        ILauncher launcher = getNewLauncher();
        if (launcher == null)
            throw new IllegalStateException("Null launcher configured");
        context.setContextObject(IContext._LAUNCHER, launcher);

        log.info("Preparing launcher: {}", launcher.getVersion());
        context.setContextObject(IContext._LAUNCHER_MASTER, this);
        Application game = launcher.createApplication();
        context.setContextObject(IContext.APPLICATION, game);
        log.info("Created application: {}", game.getVersion());

        // todo logic for queue, listeners, udp etc.
    }

    public static ILauncher getNewLauncher(){
        return switch (platformDetected) {
            case Android -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.ANDROID);
            case iOS -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.IOS);
            case Desktop -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.DESKTOP);
            case WebGL -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.WEB);
            default -> throw new IllegalStateException("Unknown platform detected");
        };
    }
}
