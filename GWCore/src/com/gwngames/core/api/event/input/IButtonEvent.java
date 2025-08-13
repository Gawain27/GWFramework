package com.gwngames.core.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.BUTTON_EVENT)
public interface IButtonEvent extends IInputEvent{
    boolean isPressed();
    float getPressure();
}
