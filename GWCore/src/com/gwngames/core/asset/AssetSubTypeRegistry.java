package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.base.cfg.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Init(module = ModuleNames.CORE)
public class AssetSubTypeRegistry extends BaseComponent implements IAssetSubTypeRegistry {

    @Inject()
    private final ILocale locale;

    private final Map<String, Collection<IAssetSubType>> byExt = new ConcurrentHashMap<>();

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
        st.extension().forEach(ext -> {
            String key = ext.ext().toLowerCase(loc);
            byExt.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>()).add(st);
        });
    }

    /** Falls back to BuiltinSubtypes.MISC */
    @Override
    public IAssetSubType byExtension(String ext) {
        if (StringUtils.isEmpty(ext))
            return null;
        Collection<IAssetSubType> c =
            byExt.get(ext.toLowerCase(locale.getLocale()));
        return (c == null || c.isEmpty())
            ? BuiltInSubTypes.MISC
            : c.iterator().next();
    }

    /** Sub-type with the given ID, or null if not present. */
    @Override
    public IAssetSubType byExtension(String ext, String id) {
        if (StringUtils.isEmpty(ext) || StringUtils.isEmpty(id))
            return null;
        Collection<IAssetSubType> c =
            byExt.get(ext.toLowerCase(locale.getLocale()));
        if (c == null) return null;
        for (IAssetSubType st : c) if (st.id().equals(id)) return st;
        return c.isEmpty()
            ? BuiltInSubTypes.MISC
            : c.iterator().next();

    }

    /** All registered sub-types for that extension (unmodifiable). */
    @Override
    public List<IAssetSubType> allByExtension(String ext) {
        if (StringUtils.isEmpty(ext))
            return List.of();
        Collection<IAssetSubType> c =
            byExt.get(ext.toLowerCase(locale.getLocale()));
        return c == null ? List.of() : List.copyOf(c);
    }
}
