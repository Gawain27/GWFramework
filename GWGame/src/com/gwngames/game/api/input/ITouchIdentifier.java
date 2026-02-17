package com.gwngames.game.api.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.TOUCH_INPUT)
public interface ITouchIdentifier extends IInputIdentifier{
    int getPointer();

    void setPointer(int pointer);
}
