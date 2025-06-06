package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.base.MacroEvent;
// TODO: to string
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.EVENT, allowMultiple = true)
public interface IEvent extends IBaseComp {
    MacroEvent getMacroEvent();
    void setMacroEvent(MacroEvent macroEvent);
}
