package com.gwngames.core.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.api.event.system.ISystemEvent;
import com.gwngames.core.api.plugin.TestEnvironmentPlugin;
import com.gwngames.core.base.cfg.PluginRegistry;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Base class for all GWFramework tests.
 * <p>
 * Subclasses implement exactly one method—{@link #runTest()}—and
 * do <strong>not</strong> add {@code @Test} themselves.
 * </p>
 *
 * <p>
 * Environment setup (e.g., libGDX globals) is delegated to {@link TestEnvironmentPlugin}
 * implementations, so gwcore stays libGDX-free.
 * </p>
 */
public abstract class BaseTest {

    protected static final FileLogger log = FileLogger.get(LogFiles.TEST);
    @Inject
    protected IConfig config;
    @Inject
    protected IMasterEventQueue master;

    @BeforeAll
    static void baseSetup() {
        FileLogger.setForceTestLogFile(true);

        // Optional environment bootstrap (provided by higher modules like gwgame)
        TestEnvironmentPlugin env = PluginRegistry.get(TestEnvironmentPlugin.class);
        if (env != null) {
            env.install();
            log.debug("TestEnvironmentPlugin installed: {} (module={}, prio={})",
                env.getClass().getName(), env.module(), env.priority());
        } else {
            log.debug("No TestEnvironmentPlugin found; running in core-only test mode.");
        }
    }

    /** JUnit entry-point – do **not** override. */
    @Test
    public final void _execute() throws Exception {
        runTest();
    }

    /** Override with your test logic. Any thrown exception fails the test. */
    protected abstract void runTest() throws Exception;

    /* ────────────────────────── convenience helpers ─────────────────────── */

    protected void assertTimeout(long timeoutMillis, Executable executable) {
        Assertions.assertTimeout(Duration.ofMillis(timeoutMillis), executable);
    }

    /**
     * Core-side common test bootstrap.
     * If your CDI graph depends on environment plugins, they should be installed before this call.
     */
    protected void setupApplication() {
        Cdi.inject(this);
        Cdi.inject(master);
        log.debug("CDI injected into {}", getClass().getSimpleName());
    }

    protected String readResourceAsString(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected JsonElement parseJson(String json) {
        return JsonParser.parseString(json);
    }

    protected void assertJsonEquals(String expectedJson, String actualJson) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement expected = parseJson(expectedJson);
        JsonElement actual   = parseJson(actualJson);
        Assertions.assertEquals(gson.toJson(expected), gson.toJson(actual));
    }

    protected Path createTempDir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    /** Short timestamp helper for log lines. */
    protected static String ts() {
        return Instant.now().toString().substring(11, 23);
    }

    /** Dummy Event stays in core (no libGDX dependency). */
    @Init(subComp = "simple_event", module = "core")
    public static final class SimpleEvent extends AbstractEvent implements ISystemEvent { }
}
