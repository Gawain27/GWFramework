package com.gwngames.game.plugin.gdx;

import com.badlogic.gdx.*;
import com.badlogic.gdx.utils.Clipboard;

public final class DummyApp implements Application {
    @Override public ApplicationType getType() { return ApplicationType.HeadlessDesktop; }
    @Override public int getVersion() { return 0; }
    @Override public long getJavaHeap() { return 0; }
    @Override public long getNativeHeap() { return 0; }
    @Override public Preferences getPreferences(String name) { return null; }
    @Override public Clipboard getClipboard() { return null; }
    @Override public void postRunnable(Runnable r) { r.run(); }
    @Override public void exit() {}
    @Override public void addLifecycleListener(LifecycleListener listener) {}
    @Override public void removeLifecycleListener(LifecycleListener listener) {}
    @Override public void debug(String tag, String message) {}
    @Override public void debug(String tag, String message, Throwable exception) {}
    @Override public void log(String tag, String message) {}
    @Override public void log(String tag, String message, Throwable exception) {}
    @Override public void error(String tag, String message) {}
    @Override public void error(String tag, String message, Throwable exception) {}
    @Override public void setLogLevel(int logLevel) {}
    @Override public int getLogLevel() { return LOG_NONE; }
    @Override public void setApplicationLogger(ApplicationLogger applicationLogger) {}
    @Override public ApplicationLogger getApplicationLogger() { return null; }

    @Override public ApplicationListener getApplicationListener() { return null; }
    @Override public Graphics getGraphics() { return null; }
    @Override public Audio getAudio() { return null; }
    @Override public Input getInput() { return null; }
    @Override public Files getFiles() { return null; }
    @Override public Net getNet() { return null; }
}
