package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Ensures ModularAssetManager.touch() is invoked on every successful
 * get(path, type) call → the 'lastUsed' entry MUST advance.
 */
public class TouchTimestampUpdateTest extends BaseTest {

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
    }
}
