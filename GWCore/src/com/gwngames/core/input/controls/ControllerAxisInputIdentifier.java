package com.gwngames.core.input.controls;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.util.ControllerMappingUtil;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.CONTROLLER_INPUT)
public class ControllerAxisInputIdentifier extends BaseComponent implements IInputIdentifier {
    private final Controller controller;
    private final int        axisCode;

    public ControllerAxisInputIdentifier(Controller controller, int axisCode) {
        this.controller = controller;
        this.axisCode   = axisCode;
    }

    public Controller getController() {
        return controller;
    }

    public int getAxisCode() {
        return axisCode;
    }

    @Override
    public String getDeviceType() {
        return controller.getName();
    }

    @Override
    public String getComponentType() {
        return "Axis";
    }

    @Override
    public String getDisplayName() {
        // maps e.g. code 1 on an Xbox pad → "Left Stick Y"
        return ControllerMappingUtil.getAxisName(controller.getName(), axisCode);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ControllerAxisInputIdentifier other)) return false;
        return other.controller.equals(controller)
            && other.axisCode == axisCode;
    }

    @Override
    public int hashCode() {
        return controller.hashCode() * 31 + Integer.hashCode(axisCode);
    }
}
