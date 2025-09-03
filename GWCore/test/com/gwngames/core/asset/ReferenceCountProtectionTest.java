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
 * • Step-1  : load dummy asset  → ref-count == 2  → NOT evicted
 * • Step-2a : drop extra ref    → ref-count == 1  (still TTL-expired)
 * • Step-2b : update() again    → NOW evicted
 */
public class ReferenceCountProtectionTest extends BaseTest {

    /** Tiny dummy object to satisfy the StubAssetManager store. */
    public static final class DummyAsset {}

    /** Custom stub that lets us spoof reference-counts. */
    public static final class RefCountingStub extends StubAssetManager {
        private final String watchAbs;
        private volatile int forced = 2;              // start with 2 refs
        RefCountingStub(String watchAbs){ this.watchAbs = watchAbs; }
        void setRefCount(int v){ this.forced = v; }
        @Override public int getReferenceCount(String n){
            return watchAbs.equals(n) ? forced : super.getReferenceCount(n);
        }
    }

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        final String REL = "foo/bar.dummy";

        // Fresh manager (no globals), wire dependencies
        ModularAssetManager mgr = new ModularAssetManager();
        Cdi.inject(mgr);

        // Resolve the absolute path the manager will use internally
        Method toAbs = ModularAssetManager.class.getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mgr, REL);

        // Ensure a physical file exists at ABS so get() passes the existence check
        Path absPath = Path.of(ABS);
        Files.createDirectories(absPath.getParent());
        boolean createdHere = false;
        if (Files.notExists(absPath)) {
            Files.write(absPath, new byte[0]); // tiny placeholder
            createdHere = true;
        }

        try {
            // Swap the internal AssetManager for our ref-counting stub (watching ABS)
            Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
            gdxF.setAccessible(true);
            RefCountingStub stub = new RefCountingStub(ABS);
            gdxF.set(mgr, stub);
            gdxF.setAccessible(false);

            // Register discovery so ensureScheduled() accepts the path
            Field discF = ModularAssetManager.class.getDeclaredField("discovered");
            discF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> discovered = (Map<String, Object>) discF.get(mgr);
            discovered.put(ABS, new Object());

            // Start with 0 until load finishes, then bump to 2 to protect from eviction
            stub.setRefCount(0);

            // Load once (manager will call through stub; key is ABS)
            mgr.get(REL, DummyAsset.class);
            stub.setRefCount(2);

            // Sanity: it should be marked loaded under ABS
            Assertions.assertTrue(stub.isLoaded(ABS), "sanity: asset was not loaded via stub");

            // Backdate last-used so it’s TTL-expired
            long defaultTtlMs;
            try {
                Field ttlF = ModularAssetManager.class.getDeclaredField("DEFAULT_TTL_MS");
                ttlF.setAccessible(true);
                defaultTtlMs = ttlF.getLong(null);
            } catch (NoSuchFieldException nsfe) {
                defaultTtlMs = 5 * 60_000L; // fallback if field renamed
            }

            Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
            lastF.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> last = (Map<String, Long>) lastF.get(mgr);
            long expired = System.currentTimeMillis() - (defaultTtlMs + 1);
            last.put(ABS, expired);

            // First update: ref-count forced to 2 → must NOT evict
            mgr.update(0f);
            Assertions.assertTrue(stub.isLoaded(ABS),
                "asset must survive while reference-count > 1");

            // Drop extra reference and update again → should evict
            stub.setRefCount(1);
            mgr.update(0f);
            Assertions.assertFalse(stub.isLoaded(ABS),
                "asset must be evicted once ref-count returns to 1");
        } finally {
            if (createdHere) {
                try { Files.deleteIfExists(absPath); } catch (Exception ignored) {}
            }
        }
    }
}
