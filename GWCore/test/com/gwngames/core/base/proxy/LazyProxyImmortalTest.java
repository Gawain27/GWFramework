package com.gwngames.core.base.proxy;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.LazyProxy;
import org.junit.jupiter.api.Assertions;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  LazyProxy IMMORTAL instance is never evicted.
 *  • TTL is forced to 50 ms to keep the test fast.
 *  • We create a proxy with immortal=true and call one of its methods.
 *  • After sleeping far longer than the TTL we call the same method again.
 *  • The supplier must have run exactly once → underlying instance reused.
 */
public class LazyProxyImmortalTest extends BaseTest {

    /* ─── tiny interface + impl for the proxy ───────────────────────── */

    interface Foo extends IBaseComp {
        int ping();         // trivial method to invoke via proxy
    }

    public static final class FooImpl extends BaseComponent implements Foo {

        private final int id;                // lets us verify identity

        FooImpl(int id) { this.id = id; }

        @Override public int ping() { return id; }   // just returns its id
    }

    /* ─── actual test ───────────────────────────────────────────────── */

    @Override
    protected void runTest() throws Exception {

        setupApplication();                  // LibGDX stubs for logger

        /* shorten global TTL to speed up the test                 */
        LazyProxy.setTtl(50);                // 50 ms

        /*  supplier that counts how many concrete objects created  */
        AtomicInteger ctorCalls = new AtomicInteger();
        var proxy = LazyProxy.of(
            Foo.class,
            () -> new FooImpl(ctorCalls.incrementAndGet()),
            true                       // <- IMMORTAL
        );

        /*  first invocation builds the real object                 */
        int first = proxy.ping();
        Assertions.assertEquals(1, first, "first ping() comes from #1 instance");
        Assertions.assertEquals(1, ctorCalls.get(), "supplier called once");

        /*  wait way past the TTL, then invoke again                 */
        Thread.sleep(120);               // > 2* TTL (safe margin)

        int second = proxy.ping();
        Assertions.assertEquals(1, second, "second ping() must reuse same instance");
        Assertions.assertEquals(1, ctorCalls.get(), "IMMORTAL proxy was never evicted");
    }
}
