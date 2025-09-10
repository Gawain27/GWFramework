package com.gwngames.core.api.input;

import com.badlogic.gdx.controllers.Controller;

public interface IButtonIdentifier extends IInputIdentifier{
    Controller getController();
    void setController(Controller controller);

    int getButtonCode();
    void setButtonCode(int buttonCode);
}
