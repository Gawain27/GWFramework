package com.gwngames.core.base.log;

import com.gwngames.core.data.LogFiles;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
final class GwLogger implements Logger {
    private final String name;
    private final FileLogger log = FileLogger.get(LogFiles.EXT);

    GwLogger(String name) { this.name = name; }

    @Override public String getName() { return name; }

    /* ── level checks (map to your FileLogger’s enabled level) ── */
    @Override public boolean isTraceEnabled() { return isDebugEnabled(); } // no trace in FileLogger; map to debug
    @Override public boolean isDebugEnabled() { return false; }
    @Override public boolean isInfoEnabled()  { return true; }
    @Override public boolean isWarnEnabled()  { return true; }
    @Override public boolean isErrorEnabled() { return true; }

    private void log(Level lvl, String msg, Object... args) {
        switch (lvl) {
            case TRACE, DEBUG -> log.debug("[" + name + "] " + msg, args);
            case INFO          -> log.info ("[" + name + "] " + msg, args);
            case WARN          -> log.info ("[WARN][" + name + "] " + msg, args); // FileLogger has no warn; map to info or error
            case ERROR         -> log.error("[" + name + "] " + msg, args);
        }
    }
    private void log(Level lvl, String msg, Throwable t) {
        switch (lvl) {
            case TRACE, DEBUG -> log.debug("[" + name + "] " + msg, t);
            case INFO          -> log.info ("[" + name + "] " + msg, t);
            case WARN          -> log.info ("[WARN][" + name + "] " + msg, t);
            case ERROR         -> log.error("[" + name + "] " + msg, t);
        }
    }

    /* ── TRACE ── */
    @Override public void trace(String msg) { log(Level.TRACE, msg); }
    @Override public void trace(String format, Object arg) { log(Level.TRACE, format, arg); }
    @Override public void trace(String format, Object arg1, Object arg2) { log(Level.TRACE, format, arg1, arg2); }
    @Override public void trace(String format, Object... arguments) { log(Level.TRACE, format, arguments); }
    @Override public void trace(String msg, Throwable t) { log(Level.TRACE, msg, t); }
    @Override public boolean isTraceEnabled(Marker marker) { return isTraceEnabled(); }
    @Override public void trace(Marker marker, String msg) { trace(msg); }
    @Override public void trace(Marker marker, String format, Object arg) { trace(format, arg); }
    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) { trace(format, arg1, arg2); }
    @Override public void trace(Marker marker, String format, Object... arguments) { trace(format, arguments); }
    @Override public void trace(Marker marker, String msg, Throwable t) { trace(msg, t); }

    /* ── DEBUG ── */
    @Override public void debug(String msg) { log(Level.DEBUG, msg); }
    @Override public void debug(String format, Object arg) { log(Level.DEBUG, format, arg); }
    @Override public void debug(String format, Object arg1, Object arg2) { log(Level.DEBUG, format, arg1, arg2); }
    @Override public void debug(String format, Object... arguments) { log(Level.DEBUG, format, arguments); }
    @Override public void debug(String msg, Throwable t) { log(Level.DEBUG, msg, t); }
    @Override public boolean isDebugEnabled(Marker marker) { return isDebugEnabled(); }
    @Override public void debug(Marker marker, String msg) { debug(msg); }
    @Override public void debug(Marker marker, String format, Object arg) { debug(format, arg); }
    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) { debug(format, arg1, arg2); }
    @Override public void debug(Marker marker, String format, Object... arguments) { debug(format, arguments); }
    @Override public void debug(Marker marker, String msg, Throwable t) { debug(msg, t); }

    /* ── INFO ── */
    @Override public void info(String msg) { log(Level.INFO, msg); }
    @Override public void info(String format, Object arg) { log(Level.INFO, format, arg); }
    @Override public void info(String format, Object arg1, Object arg2) { log(Level.INFO, format, arg1, arg2); }
    @Override public void info(String format, Object... arguments) { log(Level.INFO, format, arguments); }
    @Override public void info(String msg, Throwable t) { log(Level.INFO, msg, t); }
    @Override public boolean isInfoEnabled(Marker marker) { return isInfoEnabled(); }
    @Override public void info(Marker marker, String msg) { info(msg); }
    @Override public void info(Marker marker, String format, Object arg) { info(format, arg); }
    @Override public void info(Marker marker, String format, Object arg1, Object arg2) { info(format, arg1, arg2); }
    @Override public void info(Marker marker, String format, Object... arguments) { info(format, arguments); }
    @Override public void info(Marker marker, String msg, Throwable t) { info(msg, t); }

    /* ── WARN ── */
    @Override public void warn(String msg) { log(Level.WARN, msg); }
    @Override public void warn(String format, Object arg) { log(Level.WARN, format, arg); }
    @Override public void warn(String format, Object... arguments) { log(Level.WARN, format, arguments); }
    @Override public void warn(String format, Object arg1, Object arg2) { log(Level.WARN, format, arg1, arg2); }
    @Override public void warn(String msg, Throwable t) { log(Level.WARN, msg, t); }
    @Override public boolean isWarnEnabled(Marker marker) { return isWarnEnabled(); }
    @Override public void warn(Marker marker, String msg) { warn(msg); }
    @Override public void warn(Marker marker, String format, Object arg) { warn(format, arg); }
    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) { warn(format, arg1, arg2); }
    @Override public void warn(Marker marker, String format, Object... arguments) { warn(format, arguments); }
    @Override public void warn(Marker marker, String msg, Throwable t) { warn(msg, t); }

    /* ── ERROR ── */
    @Override public void error(String msg) { log(Level.ERROR, msg); }
    @Override public void error(String format, Object arg) { log(Level.ERROR, format, arg); }
    @Override public void error(String format, Object arg1, Object arg2) { log(Level.ERROR, format, arg1, arg2); }
    @Override public void error(String format, Object... arguments) { log(Level.ERROR, format, arguments); }
    @Override public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }
    @Override public boolean isErrorEnabled(Marker marker) { return isErrorEnabled(); }
    @Override public void error(Marker marker, String msg) { error(msg); }
    @Override public void error(Marker marker, String format, Object arg) { error(format, arg); }
    @Override public void error(Marker marker, String format, Object arg1, Object arg2) { error(format, arg1, arg2); }
    @Override public void error(Marker marker, String format, Object... arguments) { error(format, arguments); }
    @Override public void error(Marker marker, String msg, Throwable t) { error(msg, t); }
}
