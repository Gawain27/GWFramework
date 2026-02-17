package com.gwngames.game.api.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.KEY_INPUT)
public interface IKeyIdentifier extends IInputIdentifier{
    int getKeycode();
}
