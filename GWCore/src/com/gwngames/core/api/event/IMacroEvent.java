package com.gwngames.core.api.event;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

import java.util.List;

@Init(module = DefaultModule.INTERFACE, component = CoreComponent.MACRO_EVENT, allowMultiple = true)
public interface IMacroEvent extends IBaseComp {
    void addEvent(IEvent event);

    List<IEvent> getEvents();
    String getId();

    void setId(String id);
}
