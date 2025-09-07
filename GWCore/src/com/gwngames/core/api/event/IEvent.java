package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

// TODO: to string
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.EVENT, allowMultiple = true)
public interface IEvent extends IBaseComp {
    IMacroEvent getMacroEvent();
    void setMacroEvent(IMacroEvent macroEvent);
    Iterable<IExecutionCondition> getConditions();

    IEventStatus getStatus();

    void setStatus(IEventStatus status);

    void addCondition(IExecutionCondition c);
}
