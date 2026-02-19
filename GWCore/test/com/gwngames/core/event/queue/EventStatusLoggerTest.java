package com.gwngames.core.event.queue;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.base.MacroEvent;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit-test for {@link EventStatusLogger}.
 * <p>
 * We bypass the background timer and invoke the private
 * {@code log()} method directly via reflection; this isolates the test from
 * scheduler quirks in a head-less JVM while still verifying the file output.
 * </p>
 */
public final class EventStatusLoggerTest extends BaseTest {

    /* ──────────────────────────── test body ──────────────────────────── */
    @Override
    protected void runTest() throws Exception {
        log.debug("Test start");

        /* 1) prepare temp log path */
        Path tempDir = createTempDir("status-log");
        Path logFile = tempDir.resolve("status.log");
        log.debug("Log file target: {}", logFile);

        /* 2) create queue and logger */
        MasterEventQueue master = new MasterEventQueue();
        MacroEvent macro = new MacroEvent();
        macro.setId("macroLogging");
        SimpleEvent evt = new SimpleEvent();
        macro.addEvent(evt);
        master.enqueueMacroEvent(macro);

        EventStatusLogger statusLogger =
            new EventStatusLogger(); // interval irrelevant
        log.debug("EventStatusLogger instantiated");

        /* 3) invoke private log() directly */
        Method m = EventStatusLogger.class.getDeclaredMethod("log");
        m.setAccessible(true);
        m.invoke(statusLogger);
        log.debug("log() invoked reflectively");

        /* 4) assert file exists and contains expected lines */
        Assertions.assertTrue(Files.exists(logFile) && Files.size(logFile) > 0,
            "Log file should exist and be non-empty");

        String txt = Files.readString(logFile);
        log.debug("Log file content:\n{}", txt);

        Assertions.assertTrue(txt.contains("MacroEvent: macroLogging"),
            "MacroEvent line missing in log");
        Assertions.assertTrue(txt.contains(evt.getClass().getSimpleName()),
            "Event line missing in log");
    }
}
