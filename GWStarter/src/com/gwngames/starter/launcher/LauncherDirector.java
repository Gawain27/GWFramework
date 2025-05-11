package com.gwngames.starter.launcher;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
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

@Init(module = ModuleNames.GW_STARTER, component = ComponentNames.DIRECTOR)
public class LauncherDirector extends BaseComponent {
    @Inject
    IContext context;
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final ModuleClassLoader loader = ModuleClassLoader.getInstance();

    public void start(String[] args) {
        log.log("Starting up GWFrameWork");
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.

        log.log("Looking up operating system type...");
        ILauncher launcher = getNewLauncher();
        if (launcher == null)
            throw new IllegalStateException("Null launcher configured");
        context.setContextObject(IContext._LAUNCHER, launcher);

        log.log("Preparing launcher: {}", launcher.getVersion());
        context.setContextObject(IContext._DIRECTOR, this);
        Application game = launcher.createApplication();
        context.setContextObject(IContext.APPLICATION, game);
        log.log("Created application: {}", game.getVersion());

        // todo logic for queue, listeners, udp etc.
    }

    public static ILauncher getNewLauncher(){
        Application.ApplicationType type = Gdx.app.getType();

        return switch (type) {
            case Android -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.ANDROID);
            case iOS -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.IOS);
            case Desktop -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.DESKTOP);
            case WebGL -> loader.tryCreate(ComponentNames.LAUNCHER, PlatformNames.WEB);
            default -> throw new IllegalStateException("Unknown platform detected");
        };
    }
}
