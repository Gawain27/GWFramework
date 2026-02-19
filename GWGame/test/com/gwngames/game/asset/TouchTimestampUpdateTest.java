package com.gwngames.game.asset;

import com.gwngames.core.base.BaseTest;
import com.gwngames.game.api.asset.IAssetSubType;
import com.gwngames.game.api.asset.IAssetSubTypeRegistry;
import com.gwngames.game.base.GameTest;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * Ensures ModularAssetManager.touch() is invoked on every successful
 * get(path, type) call → the 'lastUsed' entry MUST advance.
 */
public class TouchTimestampUpdateTest extends GameTest {

    /** Build a manager with stub internals suitable for unit testing. */
    private static ModularAssetManager newTestManager() throws Exception {
        System.setProperty("gw.asset.ttl", "60000"); // TTL not important for this test

        ModularAssetManager mng = new ModularAssetManager();

        // 1) Provide a fake assets root (normally set in @PostInject via IPathResolver)
        Field rootF = ModularAssetManager.class.getDeclaredField("assetsRoot");
        rootF.setAccessible(true);
        Path tmpRoot = Files.createTempDirectory("mam-test-root");
        rootF.set(mng, tmpRoot);

        // 2) Swap the internal AssetManager with an in-memory stub (no file IO)
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        gdxF.set(mng, new StubAssetManager());

        // 3) Plug a minimal subtype registry (not actually used by get(String, Class))
        Field regF = ModularAssetManager.class.getDeclaredField("reg");
        regF.setAccessible(true);
        regF.set(mng, new IAssetSubTypeRegistry() {
            @Override public void register(IAssetSubType st) {}
            @Override public IAssetSubType byExtension(String ext) { return null; }
            @Override public IAssetSubType byExtension(String ext, String id) { return null; }
            @Override public java.util.List<IAssetSubType> allByExtension(String ext) { return java.util.List.of(); }
            @Override public int getMultId() { return 0; }
        });

        return mng;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void runTest() throws Exception {
        setupApplication(); // LibGDX stubs

        ModularAssetManager mam = newTestManager();

        final String REL = "dummy/hero.dat";

        // Resolve absolute path key (the manager uses absolute keys internally)
        Method toAbs = ModularAssetManager.class.getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mam, REL);

        // Ensure the physical file exists at ABS so get()'s existence check passes
        Path absPath = Path.of(ABS);
        Files.createDirectories(absPath.getParent());
        boolean createdHere = false;
        if (Files.notExists(absPath)) {
            Files.write(absPath, new byte[0]); // zero-byte placeholder
            createdHere = true;
        }

        // Register discovery so ensureScheduled() accepts the path
        Field discF = ModularAssetManager.class.getDeclaredField("discovered");
        discF.setAccessible(true);
        Map<String, Object> discovered = (Map<String, Object>) discF.get(mam);
        discovered.put(ABS, new Object());

        try {
            /* ---------- 1st get() creates + touches asset ---------------- */
            mam.get(REL, Object.class);

            Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
            lastF.setAccessible(true);
            Map<String,Long> last = (Map<String, Long>) lastF.get(mam);
            Long t1 = last.get(ABS);
            Assertions.assertNotNull(t1, "first get() must record a lastUsed entry");

            /* little pause, then fetch again ------------------------------ */
            Thread.sleep(60);
            mam.get(REL, Object.class);

            Long t2 = last.get(ABS);
            Assertions.assertNotNull(t2, "second get() must record a lastUsed entry");
            Assertions.assertTrue(t2 > t1, "second get() must refresh the lastUsed timestamp");

            /* sanity: asset still loaded ---------------------------------- */
            Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
            gdxF.setAccessible(true);
            StubAssetManager stub = (StubAssetManager) gdxF.get(mam);
            Assertions.assertTrue(stub.isLoaded(ABS), "Asset should still be loaded");
        } finally {
            // clean up placeholder file
            if (createdHere) try { Files.deleteIfExists(absPath); } catch (Exception ignored) {}

            // clean up the temporary assets root directory
            try {
                Field rootF = ModularAssetManager.class.getDeclaredField("assetsRoot");
                rootF.setAccessible(true);
                Path root = (Path) rootF.get(mam);
                if (root != null && Files.exists(root)) {
                    try (var walk = Files.walk(root)) {
                        walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
