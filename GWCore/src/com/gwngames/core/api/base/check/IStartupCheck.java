package com.gwngames.core.api.base.check;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.STARTUP_CHECK, module = ModuleNames.INTERFACE, allowMultiple = true)
public interface IStartupCheck extends IBaseComp {
}
