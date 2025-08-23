package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.AssetCategory;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that AssetSubTypeRegistry is safe under heavy concurrent
 * register / lookup traffic, with built-ins preloaded.
 */
public class RegistryThreadSafetyTest extends BaseTest {

    /** Tiny stub subtype with only id / extension. */
    private record StubType(String id) implements IAssetSubType {
        @Override public String id() { return id; }
        @Override public AssetCategory category() { return AssetCategory.MISC; }
        @Override public Class<?> libGdxClass() { return Object.class; }
        @Override public List<IFileExtension> extension() { return List.of(Ext.JPG); }
        @Override public int getMultId() { return 0; } // not used by the registry
    }

    @Override protected void runTest() throws Exception {
        setupApplication(); // LibGDX stubs (if your registry touches Files, etc.)

        IAssetSubTypeRegistry reg = new AssetSubTypeRegistry();

        // 1) Preload built-ins
        for (var t : BuiltInSubTypes.values()) {
            reg.register(t);
        }

        // Count only built-ins that map to JPG (with or without dot).
        int builtinJpgCount = (int) java.util.Arrays.stream(BuiltInSubTypes.values())
            .filter(t -> t.extension().stream().anyMatch(ext -> {
                String s = ext.ext().toLowerCase();
                return "jpg".equals(s) || ".jpg".equals(s);
            }))
            .count();

        // 2) Hammer the registry concurrently
        final int THREADS = 8;
        final int PER_THREAD = 1_000;
        var pool  = Executors.newFixedThreadPool(THREADS);
        var ready = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int idx = t;
            pool.submit(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    reg.register(new StubType("T" + idx + "-" + i));

                    // quick mixed look-ups during writes
                    reg.byExtension("jpg");
                    var all = reg.allByExtension("jpg");
                    if (all.isEmpty()) throw new IllegalStateException("Registry lost data!");
                }
                ready.countDown();
            });
        }

        boolean ok = ready.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        Assertions.assertTrue(ok, "Threads timed out – possible dead-lock!");

        // 3) Expect exactly built-ins(JPG) + 8*1000 stubs
        int expected = builtinJpgCount + THREADS * PER_THREAD;
        int actual   = reg.allByExtension("jpg").size();
        Assertions.assertEquals(expected, actual,
            "Registry must hold every concurrently registered subtype + built-ins");
    }
}
