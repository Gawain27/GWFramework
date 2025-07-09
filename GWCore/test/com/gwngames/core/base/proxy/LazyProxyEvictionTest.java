package com.gwngames.core.base.proxy;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.LazyProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *  LazyProxy eviction after idle TTL.
 *  • Global TTL is set to 40 ms for the test.
 *  • A proxy WITHOUT immortal flag is created and invoked once → instance #1.
 *  • We wait > TTL, then call again → instance #2 is constructed.
 *  • Therefore the supplier must have run exactly twice.
 */
public class LazyProxyEvictionTest extends BaseTest {

    /* ─── helper interface + impl ──────────────────────────────────── */

    interface Bar extends IBaseComp { int id(); }

    public static final class BarImpl extends BaseComponent implements Bar {
        private final int id;
        BarImpl(int id) { this.id = id; }
        @Override public int id() { return id; }
    }

    /* ─── keep original TTL to restore afterwards ──────────────────── */

    private long originalTtl;

    @Override
    protected void runTest() throws Exception {

        setupApplication();

        /* shorten the TTL for a quick test */
        originalTtl = getCurrentTtl();
        LazyProxy.setTtl(40);                // 40 ms

        /* supplier counts how often a real instance is built */
        AtomicInteger ctorCalls = new AtomicInteger();
        Bar proxy = LazyProxy.of(
            Bar.class,
            () -> new BarImpl(ctorCalls.incrementAndGet()) ,
            false                           // NOT immortal
        );

        /*  first access → one concrete object */
        Assertions.assertEquals(1, proxy.id());
        Assertions.assertEquals(1, ctorCalls.get(), "first build");

        /* wait well beyond the TTL then access again */
        Thread.sleep(120);                  // > 2× TTL
        Assertions.assertEquals(2, proxy.id(), "new instance after eviction");
        Assertions.assertEquals(2, ctorCalls.get(), "supplier called twice");
    }

    /* restore the previous TTL so other tests are unaffected */
    @AfterEach
    void restoreTtl() { LazyProxy.setTtl(originalTtl); }

    /* reflection helper – current TTL value */
    private static long getCurrentTtl() {
        try {
            var f = LazyProxy.class.getDeclaredField("TTL_MS");
            f.setAccessible(true);
            return f.getLong(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
