package com.gwngames.core.base.log;

import com.gwngames.core.base.BaseComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileLogger {
    private final FileApplicationLogger logger = new FileApplicationLogger();
    public static final int ERROR_LEVEL = 0;
    public static final int INFO_LEVEL  = 1;
    public static final int DEBUG_LEVEL = 2;

    public static int enabled_level = DEBUG_LEVEL;

    /** Toggle in case you ever want to disable dashboard tapping at runtime. */
    private static volatile boolean tapDashboard = true;

    private final String logFilePath;

    private FileLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public static FileLogger get(String logFilePath){ return new FileLogger(logFilePath); }
    public static void setLevel (int level){ enabled_level = level; }
    public static void setDashboardTap(boolean on){ tapDashboard = on; }

    // ───────────────────────── public API ─────────────────────────

    public void info(String message, Object... args) {
        if (enabled_level >= INFO_LEVEL) {
            logger.log(logFilePath, message, args);
            dashTap(LogBus.Level.INFO, message, null, args);
        }
    }

    public void info(String message, Throwable exception, Object... args) {
        if (enabled_level >= INFO_LEVEL) {
            logger.log(logFilePath, message, exception, args);
            dashTap(LogBus.Level.INFO, message, exception, args);
        }
    }

    public void error(String message, Object... args) {
        if (enabled_level >= ERROR_LEVEL) {
            logger.error(logFilePath, message, args);
            dashTap(LogBus.Level.ERROR, message, null, args);
        }
    }

    public void error(String message, Throwable exception, Object... args) {
        if (enabled_level >= ERROR_LEVEL) {
            logger.error(logFilePath, message, exception, args);
            dashTap(LogBus.Level.ERROR, message, exception, args);
        }
    }

    public void debug(String message, Object... args) {
        if (enabled_level >= DEBUG_LEVEL) {
            logger.debug(logFilePath, message, args);
            dashTap(LogBus.Level.DEBUG, message, null, args);
        }
    }

    public void debug(String message, Throwable exception, Object... args) {
        if (enabled_level >= DEBUG_LEVEL) {
            logger.debug(logFilePath, message, exception, args);
            dashTap(LogBus.Level.DEBUG, message, exception, args);
        }
    }

    // ──────────────────────── internals ─────────────────────────

    private static final Set<String> SKIP_PREFIX = Set.of(
        "com.gwngames.core.base.log.", "java.", "sun.", "jdk.", "org.slf4j", "org.apache.logging"
    );
    private static final ConcurrentHashMap<String, Boolean> IS_BASECOMP_CACHE = new ConcurrentHashMap<>();

    private static boolean skip(String cn) {
        if (cn == null) return true;
        for (String p : SKIP_PREFIX) if (cn.startsWith(p)) return true;
        return false;
    }

    /** Find the first stack frame whose class is a BaseComponent subclass. */
    private static String resolveCallerComponentClassKey() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        for (int i = 2; i < st.length; i++) {
            String cn = st[i].getClassName();
            if (skip(cn)) continue;

            Boolean hit = IS_BASECOMP_CACHE.get(cn);
            if (hit == null) {
                boolean is = false;
                try {
                    Class<?> c = Class.forName(cn, false,
                        Thread.currentThread().getContextClassLoader());
                    is = BaseComponent.class.isAssignableFrom(c);
                } catch (Throwable ignored) {}
                IS_BASECOMP_CACHE.put(cn, is);
                hit = is;
            }
            if (hit) return cn; // class-level key
        }
        return null;
    }

    private static String safeFormat(String msg, Object... args) {
        try {
            return (args == null || args.length == 0) ? msg : String.format(msg, args);
        } catch (Exception e) {
            return msg + " " + Arrays.toString(args);
        }
    }

    private static void dashTap(LogBus.Level lvl, String template, Throwable ex, Object... args) {
        if (!tapDashboard) return;
        try {
            String key = resolveCallerComponentClassKey();
            if (key != null) {
                String line = safeFormat(template, args);
                LogBus.record(key, lvl, line, ex);
            }
        } catch (Throwable ignored) {}
    }
}
