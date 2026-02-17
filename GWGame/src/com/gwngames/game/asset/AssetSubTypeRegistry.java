package com.gwngames.game.asset;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.cfg.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.util.StringUtils;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.asset.IAssetSubType;
import com.gwngames.game.api.asset.IAssetSubTypeRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Init(module = CoreModule.CORE)
public class AssetSubTypeRegistry extends BaseComponent implements IAssetSubTypeRegistry {

    @Inject
    private ILocale locale;

    private final Map<String, Collection<IAssetSubType>> byExt = new ConcurrentHashMap<>();

    @PostInject
    private void init() {
        // ensure we have a locale even if injector provided a lazy proxy later
        if (locale == null) {
            locale = BaseComponent.getInstance(ILocale.class);
        }

        // register built-ins
        for (BuiltInSubTypes st : BuiltInSubTypes.values()) {
            register(st);
        }

        // pick up extra subtypes from all modules (enums and classes)
        ModuleClassLoader.getInstance()
            .tryCreateAll(GameComponent.ASSET_SUBTYPE)
            .forEach(st -> register((IAssetSubType) st));
    }

    @Override
    public void register(IAssetSubType st) {
        Locale loc = currentLocale();
        for (var ext : st.extension()) {
            String key = ext.ext().toLowerCase(loc);
            byExt.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>()).add(st);
        }
    }

    /** Falls back to BuiltInSubTypes.MISC. */
    @Override
    public IAssetSubType byExtension(String ext) {
        if (StringUtils.isEmpty(ext)) return BuiltInSubTypes.MISC;
        var c = byExt.get(ext.toLowerCase(currentLocale()));
        return (c == null || c.isEmpty()) ? BuiltInSubTypes.MISC : c.iterator().next();
    }

    /** Sub-type with the given ID (or first matching / MISC if missing). */
    @Override
    public IAssetSubType byExtension(String ext, String id) {
        if (StringUtils.isEmpty(ext)) return BuiltInSubTypes.MISC;
        var c = byExt.get(ext.toLowerCase(currentLocale()));
        if (c == null || c.isEmpty()) return BuiltInSubTypes.MISC;
        for (IAssetSubType st : c) if (id != null && id.equals(st.id())) return st;
        return c.iterator().next();
    }

    /** All registered sub-types for that extension (unmodifiable). */
    @Override
    public List<IAssetSubType> allByExtension(String ext) {
        if (StringUtils.isEmpty(ext)) return List.of();
        var c = byExt.get(ext.toLowerCase(currentLocale()));
        return (c == null || c.isEmpty()) ? List.of() : List.copyOf(c);
    }

    private Locale currentLocale() {
        try {
            Locale l = (locale != null) ? locale.getLocale() : null;
            return (l != null) ? l : Locale.ENGLISH;
        } catch (Throwable ignored) {
            return Locale.ENGLISH;
        }
    }
}
