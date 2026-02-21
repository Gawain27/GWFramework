package com.gwngames.game.asset;

import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.api.asset.IAssetManager;
import com.gwngames.game.base.GameTest;
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
public class EvictionAfterTTLTest extends GameTest {

    private static final class CountingStub extends StubAssetManager {
        @Override public int getReferenceCount(String n) { return 1; }   // always 1
    }

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        final String REL = "textures/logo.dummy";

        // create via framework (fresh instance so other tests don’t pollute it)
        IAssetManager mgr = BaseComponent.getInstance(IAssetManager.class, true);

        // swap internal gdx manager
        Field gdxF = mgr.getClass().getDeclaredField("gdx");
        gdxF.setAccessible(true);
        CountingStub stub = new CountingStub();
        gdxF.set(mgr, stub);
        gdxF.setAccessible(false);

        // use the real method on the concrete type
        Method toAbs = mgr.getClass().getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mgr, REL);

        Path absPath = Path.of(ABS);
        Files.createDirectories(absPath.getParent());
        boolean createdHere = false;
        if (Files.notExists(absPath)) {
            Files.write(absPath, new byte[0]);
            createdHere = true;
        }

        try {
            Field discF = mgr.getClass().getDeclaredField("discovered");
            discF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> discovered = (Map<String, Object>) discF.get(mgr);
            discovered.put(ABS, new Object());

            mgr.get(REL, Object.class);

            Assertions.assertTrue(stub.isLoaded(ABS), "sanity: asset was not loaded via stub");

            long defaultTtlMs;
            try {
                Field ttlF = mgr.getClass().getDeclaredField("DEFAULT_TTL_MS");
                ttlF.setAccessible(true);
                defaultTtlMs = ttlF.getLong(null);
            } catch (NoSuchFieldException nsfe) {
                defaultTtlMs = 5 * 60_000L;
            }

            Field lastF = mgr.getClass().getDeclaredField("lastUsed");
            lastF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> lastUsed = (Map<String, Long>) lastF.get(mgr);

            long expired = System.currentTimeMillis() - (defaultTtlMs + 1);
            lastUsed.put(ABS, expired);

            mgr.update(0f);

            Assertions.assertFalse(stub.isLoaded(ABS),
                "Asset must be evicted once TTL elapsed and ref-count == 1");
        } finally {
            if (createdHere) {
                try { Files.deleteIfExists(absPath); } catch (Exception ignored) {}
            }
        }
    }
}
