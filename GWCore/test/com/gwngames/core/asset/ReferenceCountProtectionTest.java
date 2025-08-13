package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
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
        private final String watch;
        private volatile int forced = 2;              // start with 2 refs

        RefCountingStub(String watch){ this.watch = watch; }

        void setRefCount(int v){ this.forced = v; }

        @Override public int getReferenceCount(String n){
            return watch.equals(n) ? forced : super.getReferenceCount(n);
        }
    }

    @Override
    protected void runTest() throws Exception {
        /* Headless LibGDX scaffolding ----------------------------------- */
        setupApplication();

        final String PATH = "foo/bar.dummy";

        /* fresh manager + custom AssetManager stub -------------------- */
        IAssetManager mgr = BaseComponent.getInstance(IAssetManager.class);

        /* swap the internal gdx field with our stub --------------------- */
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);

        RefCountingStub stub = new RefCountingStub(PATH);
        gdxF.set(mgr, stub);
        stub.setRefCount(0);
        /* pretend to discover the asset ------------------------------- */
        Field discF = ModularAssetManager.class.getDeclaredField("discovered");
        discF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,Object> disc = (Map<String,Object>) discF.get(mgr);
        disc.put(PATH, BuiltInSubTypes.MISC);          // use a real subtype

        /* schedule + obtain the asset (first reference) -------------- */
        mgr.get(PATH, DummyAsset.class);             // schedules a load
        // schedule triggers StubAssetManager.load(..)
        stub.setRefCount(2);

        /* mark as TTL-expired right away ----------------------------- */
        Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
        lastF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,Long> last = (Map<String,Long>) lastF.get(mgr);
        long expired = System.currentTimeMillis() -
            600_001 /* DEFAULT_TTL_MS (5 min) + 1ms */;
        last.put(PATH, expired);

        /* FIRST update – ref-count forced to 2  → NOT evicted -------- */
        mgr.update(0);
        Assertions.assertTrue(stub.isLoaded(PATH),
            "asset must survive while reference-count > 1");

        /* drop extra reference, second update – should evict --------- */
        stub.setRefCount(1);                         // simulate releasing the hold
        mgr.update(0);
        Assertions.assertFalse(stub.isLoaded(PATH),
            "asset must be evicted once ref-count returns to 1");
    }
}
