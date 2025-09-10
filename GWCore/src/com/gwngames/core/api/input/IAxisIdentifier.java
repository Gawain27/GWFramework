package com.gwngames.core.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.CONTROLLER_AXIS_INPUT)
public interface IAxisIdentifier extends IInputIdentifier {
    void setController(Controller controller);
    Controller getController();

    int getAxisCode();
    void setAxisCode(int axisCode);
}
