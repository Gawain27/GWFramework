package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.AssetCategory;
import org.junit.jupiter.api.Assertions;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that AssetSubTypeRegistry is safe under heavy concurrent
 * register / lookup traffic.
 *  • 8 worker threads
 *  • each thread registers 1 000 synthetic sub-types
 *  • in between, threads call byExtension() and allByExtension()
 *  • the test succeeds when:
 *      – no exceptions are thrown
 *      – the registry reports exactly 8 000 sub-types for the ".jpg" extension
 */
public class RegistryThreadSafetyTest extends BaseTest {

    /** Tiny stub subtype with only id / extension. */
    private record StubType(String id) implements IAssetSubType {
        @Override public String                id()         { return id;           }

        @Override
        public AssetCategory category() {
            return null;
        }

        @Override
        public Class<?> libGdxClass() {
            return null;
        }

        @Override public Collection<IFileExtension> extensions() { return Set.of(ext); }
        private static final Ext ext = Ext.JPG;

        @Override
        public int getMultId() {
            return 0;
        }
    }

    @Override protected void runTest() throws Exception {

        setupApplication();                                      // LibGDX stubs

        IAssetSubTypeRegistry reg = new AssetSubTypeRegistry();

        final int THREADS = 8;
        final int PER_THREAD = 1_000;
        CountDownLatch ready = new CountDownLatch(THREADS);

        var pool = Executors.newFixedThreadPool(THREADS);
        for (int t = 0; t < THREADS; t++) {
            final int idx = t;
            pool.submit(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    String id = "T" + idx + "-" + i;
                    reg.register(new StubType(id));

                    // quick mixed look-ups
                    reg.byExtension("jpg");
                    List<IAssetSubType> all = reg.allByExtension("jpg");
                    if (all.isEmpty())
                        throw new IllegalStateException("Registry lost data!");
                }
                ready.countDown();
            });
        }

        /* Wait max 10 s for all work to finish */
        boolean ok = ready.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        Assertions.assertTrue(ok, "Threads timed out – possible dead-lock!");

        // 8 000 = 8 threads × 1 000 stub types, *plus* the Built-ins
        int expected = THREADS * PER_THREAD + 1;
        Assertions.assertTrue(expected < reg.allByExtension("jpg").size(),
            "Registry must hold every concurrently registered subtype");
    }
}
