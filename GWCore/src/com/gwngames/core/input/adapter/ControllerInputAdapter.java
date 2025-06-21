package com.gwngames.core.input.adapter;

import com.badlogic.gdx.controllers.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.input.*;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.*;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.CONTROLLER_ADAPTER)
public class ControllerInputAdapter extends BaseInputAdapter implements IInputAdapter, ControllerListener {

    private static final float DEAD_ZONE = 0.10f;

    private final Controller controller;

    public ControllerInputAdapter(Controller controller) {
        super("Controller:"+controller.getName());
        this.controller = controller;
    }

    public Controller getController() { return controller; }

    /* life-cycle ----------------------------------------------------------- */
    @Override public void start() { Controllers.addListener(this); }

    @Override public void stop()  { Controllers.removeListener(this); }

    /* ControllerListener – buttons ---------------------------------------- */
    @Override
    public boolean buttonDown(Controller c, int buttonCode) {
        if (c != controller) return false;
        dispatch(new ButtonEvent(getSlot(),
            new ControllerButtonInputIdentifier(controller,buttonCode),
            true,1f));
        return false;
    }

    @Override
    public boolean buttonUp(Controller c, int buttonCode) {
        if (c != controller) return false;
        dispatch(new ButtonEvent(getSlot(),
            new ControllerButtonInputIdentifier(controller,buttonCode),
            false,0f));
        return false;
    }

    /* axes ---------------------------------------------------------------- */
    @Override
    public boolean axisMoved(Controller c, int axisCode, float value) {
        if (c != controller) return false;
        float norm = Math.abs(value) < DEAD_ZONE ? 0f : value;
        dispatch(new AxisEvent(getSlot(),
            new ControllerAxisInputIdentifier(controller, axisCode),
            value, norm));
        return false;
    }

    /* other callbacks ignored --------------------------------------------- */
    @Override public void connected(Controller c) {}  // handled by detector
    @Override public void disconnected(Controller c) {}
}

