package com.gwngames.core.input.controls;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.util.ControllerMappingUtil;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.CONTROLLER_INPUT)
public class ControllerButtonInputIdentifier extends BaseComponent implements IInputIdentifier {
    private final Controller controller;
    private final int        buttonCode;

    public ControllerButtonInputIdentifier(Controller controller, int buttonCode) {
        this.controller = controller;
        this.buttonCode = buttonCode;
    }

    public Controller getController() {
        return controller;
    }

    public int getButtonCode() {
        return buttonCode;
    }

    @Override
    public String getDeviceType() {
        return controller.getName();
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
        if (!(o instanceof ControllerButtonInputIdentifier other)) return false;
        return other.controller.equals(controller)
            && other.buttonCode == buttonCode;
    }

    @Override
    public int hashCode() {
        return controller.hashCode() * 31 + Integer.hashCode(buttonCode);
    }
}
