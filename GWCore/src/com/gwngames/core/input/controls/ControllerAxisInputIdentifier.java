package com.gwngames.core.input.controls;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IAxisIdentifier;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.ControllerMappingUtil;

@Init(module = ModuleNames.CORE)
public class ControllerAxisInputIdentifier extends BaseInputIdentifier implements IAxisIdentifier {
    private Controller controller;
    private int axisCode;

    public ControllerAxisInputIdentifier(){}

    public ControllerAxisInputIdentifier(Controller controller, int axisCode, boolean recordWhilePressed) {
        super(recordWhilePressed);
        this.controller = controller;
        this.axisCode   = axisCode;
    }

    @Override
    public void setController(Controller controller) {
        this.controller = controller;
    }

    @Override
    public Controller getController() {
        return controller;
    }

    @Override
    public int getAxisCode() {
        return axisCode;
    }

    @Override
    public void setAxisCode(int axisCode) {
        this.axisCode = axisCode;
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
        if (controller == null) return -99 + Integer.hashCode(axisCode);
        return controller.hashCode() * 31 + Integer.hashCode(axisCode);
    }

}
