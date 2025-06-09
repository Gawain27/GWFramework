package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.base.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Init(module = ModuleNames.CORE)
public class AssetSubTypeRegistry extends BaseComponent implements IAssetSubTypeRegistry {

    @Inject()
    private final ILocale locale;

    private final Map<String, List<IAssetSubType>> byExt = new ConcurrentHashMap<>();

    public AssetSubTypeRegistry() {
        /* ensure we have a locale even before @Inject wiring finishes */
        locale = BaseComponent.getInstance(ILocale.class);
        /* register built-ins */
        Arrays.stream(BuiltInSubTypes.values()).forEach(this::register);

        /* pick up extra enums/modules */
        ModuleClassLoader.getInstance()
            .tryCreateAll(ComponentNames.ASSET_SUBTYPE)
            .forEach(subType -> this.register((IAssetSubType) subType));
    }

    @Override
    public void register(IAssetSubType st) {
        Locale loc = locale == null ? Locale.ENGLISH : locale.getLocale();
        st.extensions().forEach(ext -> {
            String key = ext.ext().toLowerCase(loc);
            byExt.computeIfAbsent(key, k -> new ArrayList<>()).add(st);
        });
    }

    /** Falls back to BuiltinSubtypes.MISC */
    @Override
    public IAssetSubType byExtension(String ext) {
        List<IAssetSubType> list = byExt.get(ext.toLowerCase(locale.getLocale()));
        return list == null || list.isEmpty() ? BuiltInSubTypes.MISC : list.getFirst();
    }

    /** Sub-type with the given ID, or null if not present. */
    @Override
    public IAssetSubType byExtension(String ext, String id) {
        List<IAssetSubType> list = byExt.get(ext.toLowerCase(locale.getLocale()));
        if (list == null) return null;
        for (IAssetSubType st : list) if (st.id().equals(id)) return st;
        return null;
    }

    /** All registered sub-types for that extension (unmodifiable). */
    @Override
    public List<IAssetSubType> allByExtension(String ext) {
        List<IAssetSubType> l = byExt.get(ext.toLowerCase(locale.getLocale()));
        return l == null ? List.of() : List.copyOf(l);
    }
}
