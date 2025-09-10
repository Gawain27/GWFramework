package com.gwngames.core.api.event;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.MACRO_EVENT, allowMultiple = true)
public interface IMacroEvent extends IBaseComp {
    void addEvent(IEvent event);

    Array<IEvent> getEvents();
    String getId();

    void setId(String id);
}
