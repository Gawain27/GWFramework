package com.gwngames.core.api.asset;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * Defines a file extension to be looked up in the asset manager
 * */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.FILE_EXTENSION, allowMultiple = true, isEnum = true)
public interface IFileExtension {
    String ext();
}
