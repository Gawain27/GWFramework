package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.util.List;

/** Unit-test for {@link ModularAssetManager}. */
public final class ModularAssetManagerTest extends BaseTest {

    @Override protected void runTest() throws Exception {

        setupApplication();
        System.setProperty("gw.asset.ttl", "150");   // 150 ms

        /* 1️⃣  make ModuleClassLoader ignore real jars */
        ModuleClassLoader mcl = ModuleClassLoader.getInstance();
        Field clField = ModuleClassLoader.class.getDeclaredField("classLoaders");
        clField.setAccessible(true);

        List<?> loaders = (List<?>) clField.get(mcl);
        loaders.clear();                 // no assets.txt will be scanned
        clField.setAccessible(false);

        /* 2️⃣  get manager via DI and inject stub AssetManager */
        IAssetManager mgr = BaseComponent.getInstance(IAssetManager.class);

        Field gdxF = ModularAssetManager.class.getDeclaredField("gdx");
        gdxF.setAccessible(true);
        StubAssetManager stub = new StubAssetManager();
        gdxF.set(mgr, stub);
        gdxF.setAccessible(false);

        /* 3️⃣  assertions: lazy-load ➜ keep ➜ evict */
        final String ASSET = "dummy.data";

        Assertions.assertFalse(stub.isLoaded(ASSET));

        mgr.get(ASSET, Object.class);          // lazy load
        Assertions.assertTrue(stub.isLoaded(ASSET));

        mgr.update(0.05f);                     // 50 ms  (< TTL)
        Assertions.assertTrue(stub.isLoaded(ASSET));

        Thread.sleep(200);                     // > TTL
        mgr.update(0.1f);
        Assertions.assertFalse(stub.isLoaded(ASSET), "asset was not evicted");
    }
}
