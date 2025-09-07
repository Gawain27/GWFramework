package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.EVENT_QUEUE, forceDefinition = true, allowMultiple = true)
public interface IEventQueue extends IBaseComp {
}
