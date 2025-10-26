package com.gwngames.core.asset;

import com.gwngames.core.data.AssetCategory;
import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.api.base.cfg.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.ModuleNames;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Locale;

/** Unit-test for {@link AssetSubTypeRegistry}. */
public final class AssetSubTypeRegistryTest extends BaseTest {

    /* ──────────────────  dummy Locale component for DI  ───────────── */

    @Init(module = ModuleNames.TEST)
    public static class TestLocale extends BaseComponent implements ILocale {
        @Override public Locale getLocale() { return Locale.ENGLISH; }
    }

    /* ──────────────────  custom sub-type for dynamic registration  ── */

    private static class XyzExt extends BaseComponent implements IFileExtension {
        @Override public String ext() { return "xyz"; }
    }

    private static class XyzSubType extends BaseComponent implements IAssetSubType {
        @Override public String        id()            { return "xyz-data"; }
        @Override public AssetCategory category()      { return AssetCategory.MISC; }
        @Override public Class<?>      libGdxClass()   { return null; }
        @Override public List<IFileExtension> extension() {
            return List.of(new XyzExt());
        }
    }

    /* ──────────────────  test logic  ──────────────────────────────── */
    @Override protected void runTest() throws Exception {

        /* instance with proper DI (TestLocale injected) */
        IAssetSubTypeRegistry reg =
            BaseComponent.getInstance(IAssetSubTypeRegistry.class);

        /* built-ins --------------------------------------------------- */
        Assertions.assertEquals(
            BuiltInSubTypes.TEXTURE,
            reg.byExtension("png"));                        // default = first

        Assertions.assertEquals(
            BuiltInSubTypes.ATLAS,
            reg.byExtension("atlas", "atlas"));              // explicit

        Assertions.assertTrue(
            reg.allByExtension("atlas").contains(BuiltInSubTypes.ATLAS));

        /* register custom --------------------------------------------- */
        IAssetSubType xyz = new XyzSubType();
        reg.register(xyz);

        Assertions.assertEquals(
            xyz, reg.byExtension("xyz"), "dynamic registration failed");
    }
}
