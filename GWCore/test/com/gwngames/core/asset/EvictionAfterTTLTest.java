package com.gwngames.core.asset;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *  3. Create a tiny placeholder file at the manager’s absolute path for <REL>.
 *  4. Call get() once → schedules & loads.
 *  5. Back-date lastUsed by (DEFAULT_TTL_MS + ε).
 *  6. Call update(0) → manager must evict the asset.
 */
public class EvictionAfterTTLTest extends BaseTest {

    private static final class CountingStub extends StubAssetManager {
        @Override public int getReferenceCount(String n) { return 1; }   // always 1
    }

    @Override
    protected void runTest() throws Exception {
        setupApplication();                         // LibGDX dummy env

        final String REL = "textures/logo.dummy";

        /* 1 ─ manager + custom internal AssetManager ------------------- */
        ModularAssetManager mgr = new ModularAssetManager();
        Cdi.inject(mgr);

        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        CountingStub stub = new CountingStub();
        gdxF.set(mgr, stub);
        gdxF.setAccessible(false);

        /* 2 ─ resolve the absolute path the manager actually uses ------ */
        Method toAbs = ModularAssetManager.class.getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mgr, REL);

        // Ensure the file physically exists so the manager’s existence check passes
        Path absPath = Path.of(ABS);
        Files.createDirectories(absPath.getParent());
        boolean createdHere = false;
        if (Files.notExists(absPath)) {
            Files.write(absPath, new byte[0]); // tiny placeholder
            createdHere = true;
        }

        try {
            /* 3 ─ register fake discovery so ensureScheduled() is fine -- */
            Field discF = ModularAssetManager.class.getDeclaredField("discovered");
            discF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> discovered = (Map<String, Object>) discF.get(mgr);
            // Key by ABS (manager normalises to absolute internally)
            discovered.put(ABS, new Object());

            /* 4 ─ initial request schedules + loads one ref ------------- */
            mgr.get(REL, Object.class);                // loads through stub, keyed by ABS

            // Sanity: should be marked loaded under ABS
            Assertions.assertTrue(stub.isLoaded(ABS), "sanity: asset was not loaded via stub");

            /* 5 ─ age it beyond the (default) TTL ----------------------- */
            long defaultTtlMs;
            try {
                Field ttlF = ModularAssetManager.class.getDeclaredField("DEFAULT_TTL_MS");
                ttlF.setAccessible(true);
                defaultTtlMs = ttlF.getLong(null);
            } catch (NoSuchFieldException nsfe) {
                defaultTtlMs = 5 * 60_000L; // fallback to 5 minutes if field name changes
            }

            Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
            lastF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> lastUsed = (Map<String, Long>) lastF.get(mgr);

            long expired = System.currentTimeMillis() - (defaultTtlMs + 1); // TTL + ε
            lastUsed.put(ABS, expired);

            /* 6 ─ single update() call should evict the asset ----------- */
            mgr.update(0f);

            Assertions.assertFalse(stub.isLoaded(ABS),
                "Asset must be evicted once TTL elapsed and ref-count == 1");
        } finally {
            // Cleanup placeholder file if we created it
            if (createdHere) {
                try { Files.deleteIfExists(absPath); } catch (Exception ignored) {}
            }
        }
    }
}
