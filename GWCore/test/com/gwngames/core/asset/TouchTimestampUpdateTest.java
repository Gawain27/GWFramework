package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Ensures ModularAssetManager.touch() is invoked on every successful
 * get(path, type) call → the 'lastUsed' entry MUST advance.
 */
public class TouchTimestampUpdateTest extends BaseTest {

    /** Injects our StubAssetManager + dummy subtype registry. */
    private static ModularAssetManager newTestManager() throws Exception {

        /* make TTL tiny so the real value doesn't matter for this test */
        System.setProperty("gw.asset.ttl", "60000");          // 60 s – any value OK

        ModularAssetManager mng = new ModularAssetManager();

        /* swap the internal AssetManager with our in-memory stub ------- */
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        gdxF.set(mng, new StubAssetManager());                // no real file IO
        gdxF.setAccessible(false);

        /* plug a simple subtype registry (maps every ext to itself) ----- */
        Field regF = ModularAssetManager.class.getDeclaredField("reg");
        regF.setAccessible(true);
        regF.set(mng, new IAssetSubTypeRegistry() {
            @Override public void register(IAssetSubType st)                           {}
            @Override public IAssetSubType byExtension(String ext)                     { return null; }
            @Override public IAssetSubType byExtension(String ext, String id)          { return null; }
            @Override public java.util.List<IAssetSubType> allByExtension(String ext)  { return java.util.List.of(); }
            @Override public int getMultId()                                           { return 0; }
        });
        regF.setAccessible(false);

        return mng;
    }

    @SuppressWarnings("unchecked")
    @Override protected void runTest() throws Exception {

        setupApplication();                                      // LibGDX stubs
        ModularAssetManager mam = newTestManager();

        final String PATH = "dummy/hero.dat";

        /* ---------- 1st get() creates + touches asset ---------------- */
        mam.get(PATH, Object.class);

        Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
        lastF.setAccessible(true);
        Map<String,Long> last = (Map<String, Long>) lastF.get(mam);
        long t1 = last.get(PATH);

        /* little pause, then fetch again ------------------------------ */
        Thread.sleep(60);          // > a few ms to guarantee delta
        mam.get(PATH, Object.class);

        long t2 = last.get(PATH);
        Assertions.assertTrue(t2 > t1,
            "Second get() must refresh the lastUsed timestamp");

        /* sanity: no unexpected unload triggered ---------------------- */
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);                      // <-- FIX: allow access
        StubAssetManager stub = (StubAssetManager) gdxF.get(mam);
        gdxF.setAccessible(false);                     // restore flag

        Assertions.assertTrue(stub.isLoaded(PATH),
            "Asset should still be loaded");
    }
}
