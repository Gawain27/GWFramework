package com.gwngames.core.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.CONTROLLER_ADAPTER)
public interface IControllerAdapter extends IInputAdapter {
    Controller getController();
}
