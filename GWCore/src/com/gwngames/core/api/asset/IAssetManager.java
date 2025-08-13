package com.gwngames.core.api.asset;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.ASSET_MANAGER, module = ModuleNames.INTERFACE)
public interface IAssetManager extends IBaseComp {
    <T> T get(String path, Class<T> as);

    <T> T get(IAssetPath asset);

    <T> T get(IAssetPath asset, Class<T> as);

    IAssetSubType subtypeOf(String path);
    boolean update(float delta);
}
