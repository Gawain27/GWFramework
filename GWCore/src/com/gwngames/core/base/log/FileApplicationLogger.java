package com.gwngames.core.base.log;

import com.badlogic.gdx.ApplicationLogger;
import com.gwngames.core.data.LogFiles;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

// TODO: set gdx app
public class FileApplicationLogger implements ApplicationLogger {
    private static final int MAX_LINES = 10_000; // Maximum lines per file
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private void logToFile(String baseFilePath, String level, String message, Throwable exception, Object... args) {

        if(baseFilePath == null){
            baseFilePath = LogFiles.DEFAULT;
        }

        try {
            // Ensure the log file is rotated if needed
            String logFilePath = getRotatedLogFile(baseFilePath);

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
                // Format message using placeholders
                String formattedMessage = String.format(message.replace("{}", "%s"), args);

                // Capture timestamp, thread ID, and caller class/method
                String timestamp = dateFormat.format(new Date());
                long threadId = Thread.currentThread().threadId();
                StackTraceElement caller = Thread.currentThread().getStackTrace()[5]; // Get the method caller
                String className = Arrays.stream(caller.getClassName().split("\\.")).toList().getLast();
                String methodName = caller.getMethodName();

                // Format the log message
                String logMessage = String.format("[%s - %d - %s:%s] [%s] %s",
                    timestamp, threadId, className, methodName, level, formattedMessage);

                writer.println(logMessage);
                System.out.println(logMessage);

                // Log the exception stack trace if provided
                if (exception != null) {
                    exception.printStackTrace(writer);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to write log to file: " + e.getMessage());
        }
    }

    // Checks if a log file needs to be rotated and returns the correct log file name
    private String getRotatedLogFile(String baseFilePath) throws IOException {
        File logFile = new File(baseFilePath);
        if (!logFile.exists()) {
            return baseFilePath;
        }

        int lineCount = countLines(logFile);
        if (lineCount < MAX_LINES) {
            return baseFilePath;
        }

        // Rotate the log file
        int fileIndex = 1;
        while (true) {
            File rotatedFile = new File(baseFilePath + "." + fileIndex);
            if (!rotatedFile.exists()) {
                logFile.renameTo(rotatedFile);
                return baseFilePath; // Continue logging to a fresh file
            }
            fileIndex++;
        }
    }

    // Counts the number of lines in the log file
    private int countLines(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int lines = 0;
            while (reader.readLine() != null) {
                lines++;
            }
            return lines;
        }
    }

    // Log methods
    private void log(String logFilePath, String level, String message, Object... args) {
        logToFile(logFilePath, level, message, null, args);
    }

    private void log(String logFilePath, String level, String message, Throwable exception, Object... args) {
        logToFile(logFilePath, level, message, exception, args);
    }

    public void log(String logFilePath, String message, Object... args) {
        log(logFilePath, "INFO", message, args);
    }

    public void log(String logFilePath, String message, Throwable exception, Object... args) {
        log(logFilePath, "INFO", message, exception, args);
    }

    public void error(String logFilePath, String message, Object... args) {
        log(logFilePath, "ERROR", message, args);
    }

    public void error(String logFilePath, String message, Throwable exception, Object... args) {
        log(logFilePath, "ERROR", message, exception, args);
    }

    public void debug(String logFilePath, String message, Object... args) {
        log(logFilePath, "DEBUG", message, args);
    }

    public void debug(String logFilePath, String message, Throwable exception, Object... args) {
        log(logFilePath, "DEBUG", message, exception, args);
    }

    @Override
    public void log(String s, String s1) {
        log(null, "INFO", s, s1);
    }

    @Override
    public void log(String s, String s1, Throwable throwable) {
        log(null, "INFO", s, s1, throwable);
    }

    @Override
    public void error(String s, String s1) {
        log(null, "ERROR", s, s1);
    }

    @Override
    public void error(String s, String s1, Throwable throwable) {
        log(null, "ERROR", s, s1, throwable);
    }

    @Override
    public void debug(String s, String s1) {
        log(null, "DEBUG", s, s1);
    }

    @Override
    public void debug(String s, String s1, Throwable throwable) {
        log(null, "DEBUG", s, s1, throwable);
    }
}
