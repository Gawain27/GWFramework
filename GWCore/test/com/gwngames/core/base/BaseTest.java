package com.gwngames.core.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Base class for all GWFramework tests.
 * <p>
 * Subclasses need to implement exactly one method—{@link #runTest()}—and
 * <strong>do not</strong> have to add the {@code @Test} annotation themselves.
 * The final wrapper method {@link #_execute()} carries the annotation once and
 * delegates to your implementation, so JUnit discovers every test without any
 * boiler‑plate.
 * </p>
 */
public abstract class BaseTest {
    protected static final FileLogger log = FileLogger.get(LogFiles.TEST);

    /** JUnit entry‑point – do **not** override. */
    @Test
    public final void _execute() throws Exception {
        runTest();
    }

    /**
     * Override this method with your test logic.
     * Any thrown exception will fail the test automatically.
     */
    protected abstract void runTest() throws Exception;

    /* ────────────────────────── convenience helpers ─────────────────────── */

    /** Assert that the executable finishes within the given timeout (ms). */
    protected void assertTimeout(long timeoutMillis, Executable executable) {
        Assertions.assertTimeout(Duration.ofMillis(timeoutMillis), executable);
    }

    /** Load a classpath resource as UTF‑8 text. */
    protected String readResourceAsString(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null)
                throw new IOException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Parse JSON into a tree for structural assertions. */
    protected JsonElement parseJson(String json) {
        return JsonParser.parseString(json);
    }

    /** Pretty‑print JSON and compare the two strings for equality. */
    protected void assertJsonEquals(String expectedJson, String actualJson) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement expected = parseJson(expectedJson);
        JsonElement actual   = parseJson(actualJson);
        Assertions.assertEquals(gson.toJson(expected), gson.toJson(actual));
    }

    /** Convenience helper for ad‑hoc temporary folders inside tests. */
    protected Path createTempDir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }
}
