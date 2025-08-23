package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

        // Use the real manager via DI and reflect its absolute-path resolver
        IAssetManager api = BaseComponent.getInstance(IAssetManager.class);
        ModularAssetManager mgr = (ModularAssetManager) api;

        Method toAbs = ModularAssetManager.class.getDeclaredMethod("toAbsolute", String.class);
        toAbs.setAccessible(true);
        final String ABS = (String) toAbs.invoke(mgr, REL);

        // Swap internal AssetManager for our ref-counting stub (watching ABS)
        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        RefCountingStub stub = new RefCountingStub(ABS);
        gdxF.set(mgr, stub);

        // Start with 0 until load finishes, then bump to 2 to protect from eviction
        stub.setRefCount(0);

        // Load once (manager will call load/get using ABS under the hood)
        mgr.get(REL, DummyAsset.class);
        stub.setRefCount(2);

        // Backdate last-used so it’s TTL-expired
        Field lastF = ModularAssetManager.class.getDeclaredField("lastUsed");
        lastF.setAccessible(true);
        @SuppressWarnings("unchecked")
        var last = (java.util.Map<String,Long>) lastF.get(mgr);
        long expired = System.currentTimeMillis() - (5 * 60_000 + 1); // default TTL + ε
        last.put(ABS, expired);

        // First update: ref-count forced to 2 → must NOT evict
        mgr.update(0);
        Assertions.assertTrue(stub.isLoaded(ABS),
            "asset must survive while reference-count > 1");

        // Drop extra reference and update again → should evict
        stub.setRefCount(1);
        mgr.update(0);
        Assertions.assertFalse(stub.isLoaded(ABS),
            "asset must be evicted once ref-count returns to 1");
    }
}
