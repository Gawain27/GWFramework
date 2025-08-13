package com.gwngames.core.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.AXIS_EVENT)
public interface IAxisEvent extends IInputEvent{
    float getRawValue();
    float getNormalizedValue();
}
