package com.gwngames.starter.build;

import com.badlogic.gdx.Application;
import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

import com.gwngames.starter.StarterComponent;
import com.gwngames.starter.launcher.LauncherMaster;

@Init(component = StarterComponent.LAUNCHER_MASTER, module = DefaultModule.INTERFACE)
public interface ILauncherMaster extends IBaseComp {
    void start(String ...args);

    static Application.ApplicationType detectPlatform () {
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
