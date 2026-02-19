package com.gwngames.game.asset;

import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.game.api.asset.IAssetManager;
import com.gwngames.game.base.GameTest;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Unit-test for {@link ModularAssetManager}. */
public final class ModularAssetManagerTest extends GameTest {

    /** Keeps the original loader list so we can put it back afterwards. */
    private List<Object> originalLoaders;

    /** Field handle cached once – avoids repeating reflection look-ups. */
    private static final Field loadersField;

    static {
        try {
            loadersField = ModuleClassLoader.class.getDeclaredField("classLoaders");
            loadersField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /* ─────────────────── JUnit scaffolding ──────────────────────── */

    @BeforeEach
    void snapshotLoaders() throws IllegalAccessException {
        // 1) get the singleton instance
        ModuleClassLoader mcl = ModuleClassLoader.getInstance();
        // 2) read the field value from that instance
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) loadersField.get(mcl);
        // 3) deep copy so we can restore it later
        originalLoaders = new ArrayList<>(list);
        // 4) clear the real list so no assets.txt files are scanned
        list.clear();
    }

    @AfterEach
    void restoreLoaders() throws IllegalAccessException {
        ModuleClassLoader mcl = ModuleClassLoader.getInstance();
        @SuppressWarnings("unchecked")
        List<Object> target = (List<Object>) loadersField.get(mcl);
        target.clear();
        target.addAll(originalLoaders);   // compiler-safe: both are List<Object>
    }

    /* ─────────────────── actual test body ───────────────────────── */

    @Override
    protected void runTest() throws Exception {
        setupApplication();                      // LibGDX stubs

        // Use a small TTL so the eviction path can be exercised quickly
        ModularAssetManager.setTtl(150);         // 150 ms

        // Obtain manager via DI and replace its internal AssetManager
        IAssetManager api = BaseComponent.getInstance(IAssetManager.class);
        ModularAssetManager mgr = (ModularAssetManager) api; // concrete instance

        // Swap out the real AssetManager for the stub (no file I/O)
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        GameTest.StubAssetManager stub = new StubAssetManager();
        gdxF.set(mgr, stub);
        gdxF.setAccessible(false);

        // We must assert against the absolute path the manager uses internally
        final String REL = "dummy.data";
        Method toAbs = ModularAssetManager.class.getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mgr, REL);

        // 🔧 Ensure the file physically exists so the manager’s existence check passes
        Path absPath = Path.of(ABS);
        Files.createDirectories(absPath.getParent());
        boolean createdHere = false;
        if (Files.notExists(absPath)) {
            Files.write(absPath, new byte[0]); // tiny placeholder
            createdHere = true;
        }

        try {
            /* ── lazy-load → keep → evict sequence ─────────────────────── */
            Assertions.assertFalse(stub.isLoaded(ABS));

            mgr.get(REL, Object.class);          // schedules & loads lazily (records ABS)
            Assertions.assertTrue(stub.isLoaded(ABS));

            mgr.update(0.05f);                   // 50 ms  (< TTL) – still loaded
            Assertions.assertTrue(stub.isLoaded(ABS));

            Thread.sleep(200);                   // > TTL
            mgr.update(0.1f);                    // eviction pass
            Assertions.assertFalse(stub.isLoaded(ABS),
                "asset was not evicted after TTL elapsed and single ref-count");
        } finally {
            // Cleanup the placeholder we created
            if (createdHere) {
                try { Files.deleteIfExists(absPath); } catch (Exception ignored) {}
            }
        }
    }
}
