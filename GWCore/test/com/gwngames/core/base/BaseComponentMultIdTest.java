package com.gwngames.core.base;

import org.junit.jupiter.api.Assertions;

import java.util.Set;
import java.util.concurrent.*;

/**
 * Verifies that:
 *   • Every *fresh* BaseComponent instance gets a larger mult-id than the previous one.
 *   • No two concurrently created instances ever share the same id.
 */
public class BaseComponentMultIdTest extends BaseTest {

    /* simple concrete component – no @Init needed because we instantiate it directly */
    private static final class FooComp extends BaseComponent { }
    private static final class BarComp extends BaseComponent { }

    @Override
    protected void runTest() throws Exception {

        /* ── 1. sequential growth ─────────────────────────────────────── */
        FooComp f1 = new FooComp();
        FooComp f2 = new FooComp();
        BarComp b1 = new BarComp();

        int id1 = f1.getMultId();
        int id2 = f2.getMultId();
        int id3 = b1.getMultId();

        Assertions.assertTrue(id2 > id1, "second FooComp should have larger id than first");
        Assertions.assertTrue(id3 > id2, "BarComp id should keep growing after FooComp ids");

        /* ── 2. uniqueness under concurrency ──────────────────────────── */
        int THREADS = 20;
        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        Set<Integer> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            exec.execute(() -> {
                try { start.await(); } catch (InterruptedException ignored) { }
                /* each thread creates one fresh instance */
                ids.add(new FooComp().getMultId());
                done.countDown();
            });
        }
        start.countDown();          // fire!
        assertTimeout(1_000, done::await);

        /* every thread should have produced a unique id */
        Assertions.assertEquals(THREADS, ids.size(),
            "all mult-ids must be unique even when created in parallel");

        exec.shutdownNow();
    }
}
