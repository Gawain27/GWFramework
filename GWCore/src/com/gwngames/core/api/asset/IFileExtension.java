package com.gwngames.core.api.asset;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

/**
 * Defines a file extension to be looked up in the asset manager
 * */
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.FILE_EXTENSION, allowMultiple = true, isEnum = true)
public interface IFileExtension extends IBaseComp {
    String ext();
}
