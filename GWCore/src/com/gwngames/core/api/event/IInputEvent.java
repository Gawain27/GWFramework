package com.gwngames.core.api.event;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_EVENT, module = ModuleNames.INTERFACE, allowMultiple = true)
public interface IInputEvent extends IEvent{
}
