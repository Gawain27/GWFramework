package com.gwngames.core.base.proxy;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.LazyProxy;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Unit-test for {@link LazyProxy}. */
public final class LazyProxyTest extends BaseTest {

    /* --------------- helpers ---------------------------------------- */

    /** Minimal component used in the test – only one extra method. */
    private interface Probe extends IBaseComp {
        int id();
    }

    /** Supplier that counts how many real instances were created. */
    private static final class CountingSupplier implements Supplier<Probe> {
        private final AtomicInteger seq = new AtomicInteger();

        @Override public Probe get() {
            int id = seq.incrementAndGet();
            return new Probe() {
                @Override
                public int getMultId() {
                    return 0;
                }

                @Override public int id()          { return id; }
            };
        }
        int count() { return seq.get(); }
    }

    /* --------------- actual test ------------------------------------ */

    @Override protected void runTest() throws Exception {

        /* use a tiny TTL so the test finishes quickly */
        System.setProperty("gw.lazy.ttl", "150");   // 150 ms
        LazyProxy.setTtl(150);
        // TODO: implement as system parameter...
        /* ---------- force-update LazyProxy.TTL_MS -------------------------- */
        java.lang.reflect.Field ttlF = LazyProxy.class.getDeclaredField("TTL_MS");
        ttlF.setAccessible(true);
        ttlF.setLong(null, 150L);                   // null = static field
        /* ------------------------------------------------------------------ */


        /* ── scenario 1: normal proxy, eviction after idle ─────────── */
        CountingSupplier s1 = new CountingSupplier();
        Probe proxy1 = LazyProxy.of(Probe.class, s1);        // not immortal

        Assertions.assertEquals(0, s1.count(), "supplier already called?");
        Assertions.assertEquals(1, proxy1.id());             // → first build
        Assertions.assertEquals(1, s1.count());

        /* rapid re-use – still same instance */
        Assertions.assertEquals(1, proxy1.id());
        Assertions.assertEquals(1, s1.count());

        /* wait past TTL → instance should be evicted */
        Thread.sleep(200);
        Assertions.assertEquals(2, proxy1.id(), "eviction failed");
        Assertions.assertEquals(2, s1.count(), "supplier should run again");

        /* ── scenario 2: immortal proxy – never evicted ────────────── */
        CountingSupplier s2 = new CountingSupplier();
        Probe proxy2 = LazyProxy.of(Probe.class, s2, true);   // immortal=true

        Assertions.assertEquals(1, proxy2.id());             // build #1
        Assertions.assertEquals(1, s2.count());

        Thread.sleep(200);                                   // > TTL
        Assertions.assertEquals(1, proxy2.id(), "immortal evicted!");
        Assertions.assertEquals(1, s2.count(), "supplier ran again!");
    }
}
