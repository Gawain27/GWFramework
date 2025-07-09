package com.gwngames.core.base.proxy;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.LazyProxy;
import org.junit.jupiter.api.Assertions;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies that LazyProxy:
 *   • defers instantiation until first method invocation;
 *   • invokes its supplier exactly once – even under heavy concurrency.
 * We test the proxy in isolation because BaseComponent relies on the same
 * class for every @Inject-ed field.
 */
public class LazyProxyConcurrencyTest extends BaseTest {

    /* --------------- a tiny interface + impl used by the proxy -------- */

    private interface Service extends IBaseComp { int ping(); }

    /** counts how many real objects are ever created */
    private static final AtomicInteger REAL_CONSTRUCTORS = new AtomicInteger();

    private static final class RealService implements Service {
        private final int id = REAL_CONSTRUCTORS.incrementAndGet();
        @Override public int ping() { return id; }

        @Override
        public int getMultId() {
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    @Override protected void runTest() throws Exception {

        /* supplier that builds the *real* object – we’ll assert it runs once */
        AtomicInteger supplierCalls = new AtomicInteger();
        var proxy = LazyProxy.of(
            Service.class,
            () -> {                             // executed the *first* time any
                supplierCalls.incrementAndGet();// thread touches the proxy
                return new RealService();       // (and never again)
            },
            false                               // immortal=FALSE is fine here
        );

        /* no eager construction ─────────────────────────────── */
        Assertions.assertEquals(0, supplierCalls.get(),
            "supplier must not be called at proxy-creation time");

        /* single instantiation under concurrency ────────────── */
        int THREADS = 32;
        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        Set<Integer> results = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            exec.execute(() -> {
                try {
                    start.await();                       // sync start
                    int value = proxy.ping();            // may throw!
                    results.add(value);
                } catch (Exception e) {
                    log.error("Worker failed", e);       // helpful for test debugging
                } finally {
                    done.countDown();                    // ALWAYS executed
                }
            });
        }

        start.countDown();
        assertTimeout(1_000, () -> done.await(1, TimeUnit.SECONDS));

        /* every thread should have hit the *same* underlying instance id */
        Assertions.assertEquals(1, results.size(),
            "all threads must share the one lazily-created target object");

        /* supplier (hence constructor) must have been executed exactly once */
        Assertions.assertEquals(1, supplierCalls.get(),
            "supplier must be invoked once");
        Assertions.assertEquals(1, REAL_CONSTRUCTORS.get(),
            "only one RealService instance may ever be built");

        exec.shutdownNow();
    }
}
