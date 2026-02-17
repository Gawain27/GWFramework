package com.gwngames.core.api.event;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

// TODO: to string
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.EVENT, allowMultiple = true)
public interface IEvent extends IBaseComp {
    IMacroEvent getMacroEvent();
    void setMacroEvent(IMacroEvent macroEvent);
    Iterable<IExecutionCondition> getConditions();

    IEventStatus getStatus();

    void setStatus(IEventStatus status);

    void addCondition(IExecutionCondition c);
}
