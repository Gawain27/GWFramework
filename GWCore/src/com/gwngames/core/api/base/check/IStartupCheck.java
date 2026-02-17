package com.gwngames.core.api.base.check;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;


@Init(component = CoreComponent.STARTUP_CHECK, module = DefaultModule.INTERFACE, allowMultiple = true)
public interface IStartupCheck extends IBaseComp {
}
