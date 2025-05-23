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

@Init(module = ModuleNames.GW_STARTER)
public class LauncherMaster extends BaseComponent implements ILauncherMaster {
    private static final Application.ApplicationType platformDetected = LauncherMaster.detectPlatform();
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
        context.setContextObject(IContext._LAUNCHER_MASTER, this);
        Application game = launcher.createApplication();
        context.setContextObject(IContext.APPLICATION, game);
        log.log("Created application: {}", game.getVersion());

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

    private static Application.ApplicationType detectPlatform () {
        // Android: android.os.Build is part of every Dalvik/ART device
        if (isPresent("android.os.Build"))
            return Application.ApplicationType.Android;

        // iOS (RoboVM): UIKit is always on the classpath there
        if (isPresent("org.robovm.apple.uikit.UIApplication"))
            return Application.ApplicationType.iOS;

        // GWT / WebGL: GWT replaces this whole method at compile time, so use a constant
        if (isWeb())
            return Application.ApplicationType.WebGL;

        // Otherwise we’re running on a desktop JVM
        return Application.ApplicationType.Desktop;
    }

    private static boolean isPresent (String fqcn) {
        try {
            Class.forName(fqcn, false, LauncherMaster.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * GWT strips `Class.forName`, so we call its own helper via reflection.
     * Wrapped in a try-catch so the JVM build doesn’t need GWT on the
     * class-path.
     */
    private static boolean isWeb() {
        try {
            Class<?> gwt = Class.forName("com.google.gwt.core.client.GWT");
            return (Boolean) gwt.getMethod("isClient").invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
