package com.gwngames.core.event.queue;

import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.cfg.IApplicationLogger;
import com.gwngames.core.api.event.IEventLogger;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.api.plugin.LoggingPlugin;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.PluginRegistry;
import com.gwngames.core.event.base.MacroEvent;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * Framework-friendly test for {@link EventStatusLogger}.
 * - Uses framework instantiation (BaseComponent + @Inject/@PostInject).
 * - Overrides LoggingPlugin so output goes to a temp file we control.
 * - Calls the private log() once via reflection to avoid scheduler flakiness.
 */
public final class EventStatusLoggerTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        // 1) prepare temp log target
        Path tempDir = createTempDir("status-log");
        Path logFile = tempDir.resolve("status.log");
        log.debug("Log file target: {}", logFile);

        // 2) override logging so FileLogger writes to our temp file
        PluginRegistry.override(LoggingPlugin.class, new TestLoggingPlugin(logFile));

        try {
            // 3) create + wire master via framework
            IMasterEventQueue master = BaseComponent.getInstance(IMasterEventQueue.class);

            // 4) enqueue one macro with one event
            MacroEvent macro = new MacroEvent();
            macro.setId("macroLogging");
            SimpleEvent evt = new SimpleEvent();
            macro.addEvent(evt);
            master.enqueueMacroEvent(macro);

            // 5) create + wire EventStatusLogger via framework (subComp-based)
            IEventLogger logger = BaseComponent.getInstance(IEventLogger.class, CoreSubComponent.EVENT_STATUS_LOGGER);

            // 6) invoke the private log() once (avoid scheduler/timers)
            Method m = logger.getClass().getDeclaredMethod("log");
            m.setAccessible(true);
            m.invoke(logger);

            // 7) shutdown background scheduler if present (so test JVM is clean)
            try {
                logger.getClass().getMethod("shutdown").invoke(logger);
            } catch (NoSuchMethodException ignored) {
                // ok: logger may not expose shutdown on interface
            }

            // 8) assert file exists and contains expected lines
            Assertions.assertTrue(Files.exists(logFile), "Log file should exist");
            Assertions.assertTrue(Files.size(logFile) > 0, "Log file should be non-empty");

            String txt = Files.readString(logFile, StandardCharsets.UTF_8);
            log.debug("Log file content:\n{}", txt);

            Assertions.assertTrue(txt.contains("MacroEvent: macroLogging"),
                "MacroEvent line missing in log");
            Assertions.assertTrue(txt.contains("SimpleEvent"),
                "Event line missing in log");
        } finally {
            // important: don’t leak plugin overrides across tests
            PluginRegistry.reset();
        }
    }

    /* =========================================================================
     * Test LoggingPlugin: route ALL logs to one temp file
     * ========================================================================= */

    private record TestLoggingPlugin(Path out) implements LoggingPlugin {

        @Override
            public String id() {
                return "test-logging";
            }

            @Override
            public String module() {
                return "";
            }

            @Override
            public int priority() {
                // Ensure it wins over any real module plugin
                return Integer.MAX_VALUE;
            }

            @Override
            public IApplicationLogger createApplicationLogger() {
                return new FileApplicationLogger(out);
            }
        }

    private record FileApplicationLogger(Path out) implements IApplicationLogger {

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
                error(tag, msg, "");
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

            @Override
            public void log(String logFilePath, String message, Object... args) {
                append("INFO", logFilePath, message, null, args);
            }

            @Override
            public void log(String logFilePath, String message, Throwable exception, Object... args) {
                append("INFO", logFilePath, message, exception, args);
            }

            @Override
            public void debug(String logFilePath, String message, Object... args) {
                append("DEBUG", logFilePath, message, null, args);
            }

            @Override
            public void debug(String logFilePath, String message, Throwable exception, Object... args) {
                append("DEBUG", logFilePath, message, exception, args);
            }

            @Override
            public void error(String logFilePath, String message, Object... args) {
                append("ERROR", logFilePath, message, null, args);
            }

            @Override
            public void error(String logFilePath, String message, Throwable exception, Object... args) {
                append("ERROR", logFilePath, message, exception, args);
            }

            private synchronized void append(String lvl, String logicalPath, String template, Throwable ex, Object... args) {
                String line = format(template, args);
                // keep it simple; include the "logical" logFilePath for debugging
                String outLine = String.format(Locale.ROOT, "[%s][%s] %s", lvl, logicalPath, line);

                if (ex != null) {
                    outLine += " | " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
                }
                outLine += "\n";

                try {
                    Files.createDirectories(out.getParent());
                    Files.writeString(out, outLine, StandardCharsets.UTF_8,
                        Files.exists(out)
                            ? new OpenOption[]{StandardOpenOption.APPEND}
                            : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND});
                } catch (IOException ioe) {
                    throw new RuntimeException("Failed writing test log file: " + out, ioe);
                }
            }

            private static String format(String template, Object... args) {
                try {
                    if (args == null || args.length == 0) return template;
                    return String.format(Locale.ROOT, template, args);
                } catch (Exception e) {
                    return template + " " + Arrays.toString(args);
                }
            }
        }
}
