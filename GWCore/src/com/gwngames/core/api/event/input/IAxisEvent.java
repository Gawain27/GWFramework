package com.gwngames.core.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.AXIS_EVENT)
public interface IAxisEvent extends IInputEvent{
    float getRawValue();
    float getNormalizedValue();

    void setControl(IInputIdentifier control);

    void setRawValue(float rawValue);

    void setNormalizedValue(float normalizedValue);
}
