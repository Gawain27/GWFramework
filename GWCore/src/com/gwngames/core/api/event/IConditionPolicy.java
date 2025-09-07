package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.INTERFACE, isEnum = true, component = ComponentNames.EXEC_CONDITION_POLICY)
public interface IConditionPolicy extends IBaseComp {
}
