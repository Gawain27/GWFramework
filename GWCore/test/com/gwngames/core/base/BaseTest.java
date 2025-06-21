package com.gwngames.core.base;

import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.input.NativeInputConfiguration;
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
import com.gwngames.core.input.KeyboardDeviceDetectorTest;
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
import java.util.concurrent.ConcurrentHashMap;

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
        if (Gdx.input == null) {           // install only if someone else hasn’t
            Gdx.input = new DummyInput();  //  put a custom stub in place
        }
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

    /* ───────────── in-memory AssetManager stub ───────────── */
    public static final class StubAssetManager extends AssetManager {
        private final ConcurrentHashMap<String,Object> store = new ConcurrentHashMap<>();
        @Override public void load(String n, Class t){ store.putIfAbsent(n,new Object()); }
        @Override public boolean isLoaded(String n){ return store.containsKey(n); }
        @SuppressWarnings("unchecked") @Override
        public <T> T get(String n, Class<T> t){ return (T) store.get(n); }
        @SuppressWarnings("unchecked")
        @Override
        public <T> T finishLoadingAsset(String fileName) { return (T) store.get(fileName); }
        @Override public void unload(String n){ store.remove(n); }
        @Override public boolean update(int ms){ return true; }
        @Override public int getReferenceCount(String n){ return store.containsKey(n)?1:0; }
    }

    /** Dummy Event */
    @Init(subComp = SubComponentNames.SIMPLE_EVENT, module = ModuleNames.CORE)
    public static final class SimpleEvent extends AbstractEvent { }

    protected static final class DummyInput implements Input {

        @Override public boolean isPeripheralAvailable(Peripheral peripheral) {
            return peripheral == Peripheral.HardwareKeyboard;
        }

        /* ---------- unused methods throw/return defaults --------------- */
        @Override public float getAccelerometerX() { return 0; }
        @Override public float getAccelerometerY() { return 0; }
        @Override public float getAccelerometerZ() { return 0; }
        @Override public float getGyroscopeX() { return 0; }
        @Override public float getGyroscopeY() { return 0; }
        @Override public float getGyroscopeZ() { return 0; }

        @Override
        public int getMaxPointers() {
            return 0;
        }

        @Override public int getRotation() { return 0; }

        @Override
        public Orientation getNativeOrientation() {
            return null;
        }

        @Override public int getX() { return 0; }
        @Override public int getX(int pointer) { return 0; }
        @Override public int getDeltaX() { return 0; }
        @Override public int getDeltaX(int pointer) { return 0; }
        @Override public int getY() { return 0; }
        @Override public int getY(int pointer) { return 0; }
        @Override public int getDeltaY() { return 0; }
        @Override public int getDeltaY(int pointer) { return 0; }
        @Override public boolean isTouched() { return false; }
        @Override public boolean justTouched() { return false; }
        @Override public boolean isTouched(int pointer) { return false; }
        @Override public float getPressure() { return 0; }
        @Override public float getPressure(int pointer) { return 0; }
        @Override public boolean isKeyPressed(int key) { return false; }

        @Override
        public boolean isKeyJustPressed(int i) {
            return false;
        }

        @Override public boolean isButtonPressed(int button) { return false; }

        @Override
        public boolean isButtonJustPressed(int i) {
            return false;
        }

        @Override public void getTextInput(TextInputListener listener, String title, String text, String hint) {}

        @Override
        public void getTextInput(TextInputListener textInputListener, String s, String s1, String s2, OnscreenKeyboardType onscreenKeyboardType) {

        }

        @Override public void setOnscreenKeyboardVisible(boolean visible) {}

        @Override
        public void setOnscreenKeyboardVisible(boolean b, OnscreenKeyboardType onscreenKeyboardType) {

        }

        @Override
        public void openTextInputField(NativeInputConfiguration nativeInputConfiguration) {

        }

        @Override
        public void closeTextInputField(boolean b) {

        }

        @Override
        public void setKeyboardHeightObserver(KeyboardHeightObserver keyboardHeightObserver) {

        }

        @Override public void vibrate(int milliseconds) {}

        @Override
        public void vibrate(int i, boolean b) {

        }

        @Override
        public void vibrate(int i, int i1, boolean b) {

        }

        @Override
        public void vibrate(VibrationType vibrationType) {

        }

        @Override public float getAzimuth() { return 0; }
        @Override public float getPitch() { return 0; }
        @Override public float getRoll() { return 0; }
        @Override public void getRotationMatrix(float[] matrix) {}
        @Override public long getCurrentEventTime() { return 0; }
        @Override public void setCatchKey(int keycode, boolean catchKey) {}
        @Override public boolean isCatchKey(int keycode) { return false; }


        @Override public void setInputProcessor(InputProcessor processor) {}
        @Override public InputProcessor getInputProcessor() { return null; }
        @Override public void setCursorCatched(boolean catched) {}
        @Override public boolean isCursorCatched() { return false; }
        @Override public void setCursorPosition(int x, int y) {}
    }
    /** Short timestamp helper for log lines. */
    protected static String ts() { return Instant.now().toString().substring(11, 23); }
}
