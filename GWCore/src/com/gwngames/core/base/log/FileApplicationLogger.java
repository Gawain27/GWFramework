package com.gwngames.core.base.log;

import com.badlogic.gdx.ApplicationLogger;
import com.gwngames.core.data.LogFiles;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;
public class FileApplicationLogger implements ApplicationLogger {
    // TODO: flag to store logs internally instead, to send for diagnostic
    // we do not want to disable logs in production, just to hide them
    // TODO: if internally, then must be compressed after reaching a certain threshold
    // then, to add log threshold consts to config...
    private static final int MAX_LINES = 10_000;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /* ------------------------------------------------------------ */
    /*  Core helpers                                                */
    /* ------------------------------------------------------------ */
    private void logToFile(String baseFilePath,
                           String level,
                           String message,
                           Throwable exception,
                           Object... args) {

        if (baseFilePath == null) baseFilePath = LogFiles.DEFAULT;

        try {
            Path resolvedPath = resolveAndRotate(baseFilePath);
            // Ensure parent dirs exist
            Files.createDirectories(resolvedPath.getParent());

            try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(resolvedPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {

                // Substitute `{}` placeholders
                String formattedMessage = String.format(message.replace("{}", "%s"), args);

                // Caller & context info
                String timestamp  = dateFormat.format(new Date());
                long   threadId   = Thread.currentThread().threadId();
                StackTraceElement caller = Thread.currentThread().getStackTrace()[5];
                String className  = caller.getClassName().substring(caller.getClassName().lastIndexOf('.') + 1);
                String methodName = caller.getMethodName();

                String logLine = String.format("[%s - %d - %s:%s] [%s] %s",
                    timestamp, threadId, className, methodName, level, formattedMessage);

                writer.println(logLine);
                System.out.println(logLine);

                if (exception != null) {
                    write(exception, writer, level);
                }
            }
        } catch (IOException ioe) {
            System.err.println("Failed to write log: " + ioe.getMessage());
        }
    }

    /** Helper to log exceptions only on error */
    private void write(Throwable exception, PrintWriter writer, String level) {
        if (exception == null) return;
        // Only dump stacks for ERRORs (tweak if you also want WARN)
        if ("ERROR".equals(level)) exception.printStackTrace(writer);
    }

    /** Resolve relative path, rotate if MAX_LINES reached, return *current* log file path. */
    private Path resolveAndRotate(String baseFilePath) throws IOException {
        Path basePath = Paths.get(baseFilePath).normalize()
            .toAbsolutePath();                        // handles “../”

        if (!Files.exists(basePath)) return basePath;

        try(Stream<String> f = Files.lines(basePath)){
            long lineCount = f.count();
            if (lineCount < MAX_LINES) return basePath;
        }

        // Rotate: append .1, .2, … until a free slot is found
        int idx = 1;
        while (true) {
            Path rotated = basePath.resolveSibling(basePath.getFileName() + "." + idx);
            if (!Files.exists(rotated)) {
                Files.move(basePath, rotated);
                break;
            }
            idx++;
        }
        return basePath;  // start fresh
    }

    /* ------------------------------------------------------------ */
    /*  Convenience wrappers                                        */
    /* ------------------------------------------------------------ */
    private void log(String file, String level, String msg, Object... args) {
        logToFile(file, level, msg, null, args);
    }
    private void log(String file, String level, String msg, Throwable ex, Object... args) {
        logToFile(file, level, msg, ex, args);
    }

    public void log(String file, String msg, Object... args)                   { log(file, "INFO",  msg, args); }
    public void log(String file, String msg, Throwable ex, Object... args)     { log(file, "INFO",  msg, ex, args); }
    public void error(String file, String msg, Object... args)                 { log(file, "ERROR", msg, args); }
    public void error(String file, String msg, Throwable ex, Object... args)   { log(file, "ERROR", msg, ex, args); }
    public void debug(String file, String msg, Object... args)                 { log(file, "DEBUG", msg, args); }
    public void debug(String file, String msg, Throwable ex, Object... args)   { log(file, "DEBUG", msg, ex, args); }

    /* ------------------------------------------------------------ */
    /*  ApplicationLogger interface                                 */
    /* ------------------------------------------------------------ */
    @Override public void log(String tag, String msg)                       { log(null, tag + ": {}", msg); }
    @Override public void log(String tag, String msg, Throwable ex)         { log(null, tag + ": {}", ex, msg); }
    @Override public void error(String tag, String msg)                     { error(null, tag + ": {}", msg); }
    @Override public void error(String tag, String msg, Throwable ex)       { error(null, tag + ": {}", ex, msg); }
    @Override public void debug(String tag, String msg)                     { debug(null, tag + ": {}", msg); }
    @Override public void debug(String tag, String msg, Throwable ex)       { debug(null, tag + ": {}", ex, msg); }
}
