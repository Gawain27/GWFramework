package com.gwngames.game.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.CONTROLLER_ADAPTER)
public interface IControllerAdapter extends IInputAdapter {
    Controller getController();

    void setController(Controller controller);
}
