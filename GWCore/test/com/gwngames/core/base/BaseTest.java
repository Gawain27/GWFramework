package com.gwngames.core.base;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Clipboard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.base.AbstractEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
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

    protected void setupApplication(){
        Gdx.app   = new DummyApp();
        Gdx.files = new DummyFiles();
        log.debug("DummyApp + DummyFiles installed");
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

    /* ────────────────────────── LibGDX stubs ─────────────────────────── */

    /** Minimal {@link Application} implementation – enough for Timer. */
    public static final class DummyApp implements Application {
        public ApplicationType getType()                    { return ApplicationType.HeadlessDesktop; }
        public int             getVersion()                 { return 0; }
        public long            getJavaHeap()                { return 0; }
        public long            getNativeHeap()              { return 0; }
        public Preferences getPreferences(String name)  { return null; }
        public Clipboard getClipboard()               { return null; }
        public void            postRunnable(Runnable r)     { r.run(); }
        public void            exit()                       {}
        public void            addLifecycleListener(LifecycleListener l) {}
        public void            removeLifecycleListener(LifecycleListener l) {}
        public void            debug(String tag, String msg) {}
        public void            debug(String tag, String msg, Throwable t) {}

        @Override
        public ApplicationListener getApplicationListener() {
            return null;
        }

        @Override
        public Graphics getGraphics() {
            return null;
        }

        @Override
        public Audio getAudio() {
            return null;
        }

        @Override
        public Input getInput() {
            return null;
        }

        @Override
        public com.badlogic.gdx.Files getFiles() {
            return null;
        }

        @Override
        public Net getNet() {
            return null;
        }

        public void            log(String tag, String msg) {}
        public void            log(String tag, String msg, Throwable t) {}
        public void            error(String tag, String msg) {}
        public void            error(String tag, String msg, Throwable t) {}
        public void            setLogLevel(int level) {}
        public int             getLogLevel()               { return LOG_NONE; }

        @Override
        public void setApplicationLogger(ApplicationLogger applicationLogger) {

        }

        @Override
        public ApplicationLogger getApplicationLogger() {
            return null;
        }
    }

    /* ────────────────────────── Files stub (for FileLogger) ──────────── */
    public static final class DummyFiles implements com.badlogic.gdx.Files {
        private FileHandle fh(String p, FileType t){ return new FileHandle(new File(p)); }
        public FileHandle classpath(String p){ return fh(p, FileType.Classpath); }
        public FileHandle internal (String p){ return fh(p, FileType.Internal ); }
        public FileHandle external (String p){ return fh(p, FileType.External ); }
        public FileHandle absolute (String p){ return fh(p, FileType.Absolute ); }
        public FileHandle local    (String p){ return fh(p, FileType.Local    ); }

        @Override
        public String getExternalStoragePath() {
            return "";
        }

        public FileHandle getFileHandle(String p, FileType t){ return fh(p, t);}
        public boolean isExternalStorageAvailable(){ return true; }

        @Override
        public String getLocalStoragePath() {
            return "";
        }

        public boolean isLocalStorageAvailable()   { return true; }
    }

    /** Dummy Event */
    @Init(subComp = SubComponentNames.SIMPLE_EVENT, module = ModuleNames.CORE)
    public static final class SimpleEvent extends AbstractEvent { }

    /** Short timestamp helper for log lines. */
    protected static String ts() { return Instant.now().toString().substring(11, 23); }
}
