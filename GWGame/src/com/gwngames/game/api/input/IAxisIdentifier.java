package com.gwngames.game.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.CONTROLLER_AXIS_INPUT)
public interface IAxisIdentifier extends IInputIdentifier {
    void setController(Controller controller);
    Controller getController();

    int getAxisCode();
    void setAxisCode(int axisCode);
}
