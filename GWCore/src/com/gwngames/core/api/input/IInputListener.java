package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_LISTENER, module = ModuleNames.INTERFACE, allowMultiple = true)
public interface IInputListener extends IBaseComp {
    /** Called whenever an InputEvent is dispatched. */
    void onInput(IInputEvent event);
    String identity();
}
