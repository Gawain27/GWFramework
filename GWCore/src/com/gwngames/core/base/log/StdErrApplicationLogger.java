package com.gwngames.core.base.log;

import com.gwngames.core.api.base.cfg.IApplicationLogger;
import com.gwngames.core.util.StringUtils;

public class StdErrApplicationLogger implements IApplicationLogger {

    @Override
    public void log(String tag, String msg) {
        log(tag, msg, new Object[0]);
    }

    @Override
    public void log(String tag, String msg, Throwable ex) {
        log(tag, msg, ex, new Object[0]);
    }

    @Override
    public void error(String tag, String msg) {
        error(tag, msg, new Object[0]); // FIX: msg, not tag
    }

    @Override
    public void error(String tag, String msg, Throwable ex) {
        error(tag, msg, ex, new Object[0]);
    }

    @Override
    public void debug(String tag, String msg) {
        debug(tag, msg, new Object[0]);
    }

    @Override
    public void debug(String tag, String msg, Throwable ex) {
        debug(tag, msg, ex, new Object[0]);
    }

    @Override
    public void log(String path, String msg, Object... args) {
        print("INFO", path, StringUtils.formatBraces(msg, args), null);
    }

    @Override
    public void log(String path, String msg, Throwable t, Object... args) {
        print("INFO", path, StringUtils.formatBraces(msg, args), t);
    }

    @Override
    public void debug(String path, String msg, Object... args) {
        print("DEBUG", path, StringUtils.formatBraces(msg, args), null);
    }

    @Override
    public void debug(String path, String msg, Throwable t, Object... args) {
        print("DEBUG", path, StringUtils.formatBraces(msg, args), t);
    }

    @Override
    public void error(String path, String msg, Object... args) {
        print("ERROR", path, StringUtils.formatBraces(msg, args), null);
    }

    @Override
    public void error(String path, String msg, Throwable t, Object... args) {
        print("ERROR", path, StringUtils.formatBraces(msg, args), t);
    }

    private static void print(String level, String path, String message, Throwable t) {
        System.err.println("[" + level + "][" + path + "] " + message);
        if (t != null) t.printStackTrace(System.err);
    }
}
