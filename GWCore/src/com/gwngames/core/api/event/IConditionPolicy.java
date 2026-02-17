package com.gwngames.core.api.event;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

@Init(module = DefaultModule.INTERFACE, isEnum = true, component = CoreComponent.EXEC_CONDITION_POLICY)
public interface IConditionPolicy extends IBaseComp {
}
