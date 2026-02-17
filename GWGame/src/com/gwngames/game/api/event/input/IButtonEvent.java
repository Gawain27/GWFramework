package com.gwngames.game.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.BUTTON_EVENT)
public interface IButtonEvent extends IInputEvent {
    boolean isPressed();
    float getPressure();

    void setControl(IInputIdentifier control);

    void setPressed(boolean pressed);

    void setPressure(float pressure);
}
