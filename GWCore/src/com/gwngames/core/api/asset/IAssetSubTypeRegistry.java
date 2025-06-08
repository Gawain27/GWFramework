package com.gwngames.core.api.asset;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.SUBTYPE_REGISTRY, module = ModuleNames.INTERFACE)
public interface IAssetSubTypeRegistry extends IBaseComp {
    void register(IAssetSubType st);
    IAssetSubType byExtension(String ext);
}
