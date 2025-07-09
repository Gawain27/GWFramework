package com.gwngames.starter.launcher;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.gwngames.core.api.build.IGameLauncher;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.starter.build.ILauncher;
import com.gwngames.starter.build.LauncherVersion;

/** Launches the desktop (LWJGL3) application. */
@Init(module = ModuleNames.STARTER, platform = PlatformNames.DESKTOP)
public class Lwjgl3Launcher extends BaseComponent implements ILauncher {
    private final ModuleClassLoader loader = ModuleClassLoader.getInstance();
    private IGameLauncher launcher;

    @Override
    public Lwjgl3Application createApplication() {
        launcher = loader.tryCreate(ComponentNames.GAME);
        return new Lwjgl3Application(launcher, getDefaultConfiguration());
    }

    @Override
    public String getVersion() {
        return LauncherVersion.DESKTOP.getVersion();
    }

    private Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(launcher.getLauncherName());
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(640, 480);
        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}

