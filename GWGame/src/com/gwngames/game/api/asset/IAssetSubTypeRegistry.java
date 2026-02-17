package com.gwngames.game.api.asset;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

import java.util.List;

@Init(component = GameComponent.SUBTYPE_REGISTRY, module = DefaultModule.INTERFACE)
public interface IAssetSubTypeRegistry extends IBaseComp {
    void register(IAssetSubType st);
    IAssetSubType byExtension(String ext);
    List<IAssetSubType> allByExtension(String ext);
    IAssetSubType byExtension(String ext, String id);
}
