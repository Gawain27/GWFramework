package com.gwngames.game.input.controls;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IButtonIdentifier;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.ControllerMappingUtil;

@Init(module = ModuleNames.CORE)
public class ControllerButtonInputIdentifier extends BaseInputIdentifier implements IButtonIdentifier {
    private Controller controller;
    private int buttonCode;

    public ControllerButtonInputIdentifier(Controller controller, int buttonCode, boolean recordWhilePressed) {
        super(recordWhilePressed);
        this.controller = controller;
        this.buttonCode = buttonCode;
    }

    @Override
    public Controller getController() {
        return controller;
    }

    @Override
    public void setController(Controller controller) {
        this.controller = controller;
    }

    @Override
    public int getButtonCode() {
        return buttonCode;
    }

    @Override
    public void setButtonCode(int buttonCode) {
        this.buttonCode = buttonCode;
    }

    @Override
    public String getDeviceType() {
        // fall back gracefully when controller is not wired yet
        return "controller" + (controller != null ? ":" + controller.getName() : "");
    }


    @Override
    public String getComponentType() {
        return "Button";
    }

    @Override
    public String getDisplayName() {
        // maps e.g. code 3 on an Xbox pad → "Y"
        return ControllerMappingUtil.getButtonName(controller.getName(), buttonCode) + " Button";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof ControllerButtonInputIdentifier other)) return false;
        if (other.controller == null) return false;
        return other.controller.equals(controller)
            && other.buttonCode == buttonCode;
    }

    @Override
    public int hashCode() {
        if (controller == null) return -199 + Integer.hashCode(buttonCode);
        return controller.hashCode() * 31 + Integer.hashCode(buttonCode);
    }
}
