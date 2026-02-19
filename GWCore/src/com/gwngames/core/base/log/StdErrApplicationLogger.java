package com.gwngames.core.base.log;

import com.gwngames.core.api.base.cfg.IApplicationLogger;

public class StdErrApplicationLogger implements IApplicationLogger {
    @Override
    public void log(String tag, String msg) {
        log(tag, msg, "");
    }

    @Override
    public void log(String tag, String msg, Throwable ex) {
        log(tag, msg, ex, "");
    }

    @Override
    public void error(String tag, String msg) {
        error(tag, tag, "");
    }

    @Override
    public void error(String tag, String msg, Throwable ex) {
        error(tag, msg, ex, "");
    }

    @Override
    public void debug(String tag, String msg) {
        debug(tag, msg, "");
    }

    @Override
    public void debug(String tag, String msg, Throwable ex) {
        debug(tag, msg, ex, "");
    }

    @Override public void log(String path, String msg, Object... args) {
        System.err.println("[INFO][" + path + "] " + String.format(msg, args));
    }
    @Override public void log(String path, String msg, Throwable t, Object... args) {
        System.err.println("[INFO][" + path + "] " + String.format(msg, args));
        if (t != null) t.printStackTrace(System.err);
    }
    @Override public void debug(String path, String msg, Object... args) { log(path, msg, args); }
    @Override public void debug(String path, String msg, Throwable t, Object... args) { log(path, msg, t, args); }
    @Override public void error(String path, String msg, Object... args) { log(path, msg, args); }
    @Override public void error(String path, String msg, Throwable t, Object... args) { log(path, msg, t, args); }
}
