package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.base.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Init(component = ComponentNames.SUBTYPE_REGISTRY) // overridable
public class AssetSubTypeRegistry extends BaseComponent implements IAssetSubTypeRegistry {

    @Inject()
    private ILocale locale;

    private final Map<String, IAssetSubType> byExt = new ConcurrentHashMap<>();

    public AssetSubTypeRegistry() {
        /* register built-ins */
        Arrays.stream(BuiltInSubTypes.values()).forEach(this::register);

        /* pick up extra enums/modules */
        ModuleClassLoader.getInstance()
            .tryCreateAll(ComponentNames.ASSET_SUBTYPE)
            .forEach(subType -> this.register((IAssetSubType) subType));
    }

    @Override
    public void register(IAssetSubType st) {
        st.extensions().forEach(ext ->
            byExt.put(ext.ext().toLowerCase(locale.getLocale()), st));
    }

    /** Falls back to BuiltinSubtypes.MISC */
    @Override
    public IAssetSubType byExtension(String ext){
        return byExt.getOrDefault(ext.toLowerCase(locale.getLocale()),
            BuiltInSubTypes.MISC);
    }
}
