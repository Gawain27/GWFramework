package com.gwngames.core.base.log;

public class FileLogger {
    private final FileApplicationLogger logger = new FileApplicationLogger();
    private static final int ERROR_LEVEL = 0;
    private static final int INFO_LEVEL = 1;
    private static final int DEBUG_LEVEL = 2;

    private static int enabled_level = DEBUG_LEVEL;

    private final String logFilePath;

    private FileLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }


    public static FileLogger get(String logFilePath){
        return new FileLogger(logFilePath);
    }

    public static void setLevel (int level){
        enabled_level = level;
    }

    public void log(String message, Object... args) {
        if (enabled_level >= INFO_LEVEL)
            logger.log(logFilePath, message, args);
    }

    public void log(String message, Throwable exception, Object... args) {
        if (enabled_level >= INFO_LEVEL)
            logger.log(logFilePath, message, exception, args);
    }

    public void error(String message, Object... args) {
        if (enabled_level >= ERROR_LEVEL)
            logger.error(logFilePath, message, args);
    }

    public void error(String message, Throwable exception, Object... args) {
        if (enabled_level >= ERROR_LEVEL)
            logger.error(logFilePath, message, exception, args);
    }

    public void debug(String message, Object... args) {
        if (enabled_level >= DEBUG_LEVEL)
            logger.debug(logFilePath, message, args);
    }

    public void debug(String message, Throwable exception, Object... args) {
        if (enabled_level >= DEBUG_LEVEL)
            logger.debug(logFilePath, message, exception, args);
    }
}

