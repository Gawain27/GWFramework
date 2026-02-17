package com.gwngames.game.api.event.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.TOUCH_EVENT)
public interface ITouchEvent extends IInputEvent{
    Vector2 getPosition();
    float getPressure();

    void setControl(IInputIdentifier control);

    void setPosition(Vector2 position);

    void setPressure(float pressure);
}
