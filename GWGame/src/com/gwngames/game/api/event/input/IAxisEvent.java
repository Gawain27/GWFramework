package com.gwngames.game.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.AXIS_EVENT)
public interface IAxisEvent extends IInputEvent {
    float getRawValue();
    float getNormalizedValue();

    void setControl(IInputIdentifier control);

    void setRawValue(float rawValue);

    void setNormalizedValue(float normalizedValue);
}
