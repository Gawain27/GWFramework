package com.gwngames.core.base;

import org.junit.jupiter.api.Assertions;

import java.util.Set;
import java.util.concurrent.*;

public class BaseComponentMultIdTest extends BaseTest {

    /** Minimal concrete components to exercise BaseComponent’s id assignment. */
    static final class FooComp extends BaseComponent {}
    static final class BarComp extends BaseComponent {}

    @Override
    protected void runTest() throws Exception {
        /* 1) sequential growth on fresh instances */
        FooComp f1 = new FooComp();
        FooComp f2 = new FooComp();
        BarComp b1 = new BarComp();

        int id1 = f1.getMultId();
        int id2 = f2.getMultId();
        int id3 = b1.getMultId();

        Assertions.assertTrue(id2 > id1, "second FooComp should have larger id than first");
        Assertions.assertTrue(id3 > id2, "BarComp id should keep growing after FooComp ids");

        /* 2) uniqueness under concurrency */
        final int THREADS = 64;
        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        Set<Integer> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            exec.execute(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                ids.add(new FooComp().getMultId());
                done.countDown();
            });
        }

        start.countDown();
        assertTimeout(2_000, done::await);

        Assertions.assertEquals(THREADS, ids.size(),
            "all mult-ids must be unique even when created in parallel");

        exec.shutdownNow();
    }
}
