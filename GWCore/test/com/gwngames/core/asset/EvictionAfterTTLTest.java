package com.gwngames.core.asset;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Behaviour-under-test
 * --------------------
 *  • Asset stays resident until its “last–used” timestamp is older than
 *    DEFAULT_TTL_MS  _and_  AssetManager#getReferenceCount(path) == 1.
 * Test strategy
 * -------------
 *  1. Use a custom StubAssetManager that always reports ref-count == 1.
 *  2. Inject it into a fresh ModularAssetManager instance via reflection.
 *  3. Pretend we discovered <PATH>.dummy and call get() once → schedules load.
 *  4. Manually back-date the lastUsed entry by (TTL + δ) to simulate idleness.
 *  5. Call update(0)   →  the manager must evict the asset.
 */
public class EvictionAfterTTLTest extends BaseTest {

    private static final class CountingStub extends StubAssetManager {
        @Override public int getReferenceCount(String n) { return 1; }   // always 1
    }

    @Override
    protected void runTest() throws Exception {

        setupApplication();                         // LibGDX dummy env

        final String PATH = "textures/logo.dummy";

        /* 1 ─ manager + custom internal AssetManager ------------------- */
        ModularAssetManager mgr = new ModularAssetManager();
        Cdi.inject(mgr);

        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        CountingStub stub = new CountingStub();
        gdxF.set(mgr, stub);

        /* 2 ─ register fake discovery entry so ensureScheduled() passes */
        Field discF = ModularAssetManager.class.getDeclaredField("discovered");
        discF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,Object> discovered = (Map<String,Object>) discF.get(mgr);
        discovered.put(PATH, new Object());

        /* 3 ─ initial request schedules + loads one ref ---------------- */
        mgr.get(PATH, Object.class);                // loads through stub

        /* 4 ─ age it beyond the TTL ----------------------------------- */
        Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
        lastF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,Long> lastUsed = (Map<String,Long>) lastF.get(mgr);

        long expired = System.currentTimeMillis() -
            (5 * 60_000 + 1);            // DEFAULT_TTL_MS + ε
        lastUsed.put(PATH, expired);

        /* 5 ─ single update() call should evict the asset -------------- */
        mgr.update(0f);

        Assertions.assertFalse(stub.isLoaded(PATH),
            "Asset must be evicted once TTL elapsed and ref-count == 1");
    }
}
