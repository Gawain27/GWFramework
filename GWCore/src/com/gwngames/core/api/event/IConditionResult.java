package com.gwngames.core.api.event;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

@Init(module = DefaultModule.INTERFACE, component = CoreComponent.EXEC_CONDITION_RESULT, isEnum = true)
public interface IConditionResult extends IBaseComp {
}
