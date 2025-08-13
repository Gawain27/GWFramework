package com.gwngames.core.api.event.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.TOUCH_EVENT)
public interface ITouchEvent extends IInputEvent{
    public Vector2 getPosition();
    public float getPressure();
}
