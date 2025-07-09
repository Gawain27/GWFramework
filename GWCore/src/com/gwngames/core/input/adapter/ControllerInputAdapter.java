package com.gwngames.core.input.adapter;

import com.badlogic.gdx.controllers.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.IdentifierDefinition;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.input.AxisEvent;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.ControllerAxisInputIdentifier;
import com.gwngames.core.input.controls.ControllerButtonInputIdentifier;

import java.util.HashMap;
import java.util.Map;

/**
 * LibGDX controller → GW&nbsp;input‐event adapter.
 *
 * <p>The constructor builds two caches so run-time callbacks never need to
 * allocate a new {@link IInputIdentifier}:</p>
 * <ul>
 *   <li>{@code btnId.get(buttonCode)}</li>
 *   <li>{@code axisId.get(axisCode)}</li>
 * </ul>
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.CONTROLLER_ADAPTER)
public final class ControllerInputAdapter
    extends BaseInputAdapter
    implements IInputAdapter, ControllerListener {

    private static final float DEAD_ZONE = 0.10f;

    private final Controller controller;

    /* one identifier instance per (button / axis) ---------------------- */
    private final Map<Integer, ControllerButtonInputIdentifier> btnId = new HashMap<>();
    private final Map<Integer, ControllerAxisInputIdentifier>   axisId = new HashMap<>();

    /* ─────────────────────────── ctor & life-cycle ──────────────────── */
    public ControllerInputAdapter(Controller controller) {
        super("Controller:" + controller.getName());
        this.controller = controller;

        /* ── build the lookup caches once ─────────────────────────── */
        for (IdentifierDefinition def : IdentifierDefinition.values()) {
            for (IInputIdentifier raw : def.ids()) {

                /* buttons */
                if (raw instanceof ControllerButtonInputIdentifier tmpl
                    && tmpl.getButtonCode() >= 0) {
                    btnId.putIfAbsent(
                        tmpl.getButtonCode(),
                        new ControllerButtonInputIdentifier(
                            controller, tmpl.getButtonCode(), tmpl.isRecordWhilePressed()));
                }

                /* axes */
                if (raw instanceof ControllerAxisInputIdentifier tmpl) {
                    axisId.putIfAbsent(
                        tmpl.getAxisCode(),
                        new ControllerAxisInputIdentifier(
                            controller, tmpl.getAxisCode(), tmpl.isRecordWhilePressed()));
                }
            }
        }
    }

    public Controller getController() { return controller; }

    @Override public void start() { Controllers.addListener(this); }
    @Override public void stop()  { Controllers.removeListener(this); }

    /* ───────────────────── ControllerListener callbacks ────────────── */
    @Override
    public boolean buttonDown(Controller c, int buttonCode) {
        if (c != controller) return false;
        ControllerButtonInputIdentifier id = btnId.get(buttonCode);
        if (id == null) return false;               // unmapped button
        dispatch(new ButtonEvent(getSlot(), id, true, 1f));
        return false;
    }

    @Override
    public boolean buttonUp(Controller c, int buttonCode) {
        if (c != controller) return false;
        ControllerButtonInputIdentifier id = btnId.get(buttonCode);
        if (id == null) return false;
        dispatch(new ButtonEvent(getSlot(), id, false, 0f));
        return false;
    }

    @Override
    public boolean axisMoved(Controller c, int axisCode, float value) {
        if (c != controller) return false;
        ControllerAxisInputIdentifier id = axisId.get(axisCode);
        if (id == null) return false;               // unmapped axis
        float norm = Math.abs(value) < DEAD_ZONE ? 0f : value;
        dispatch(new AxisEvent(getSlot(), id, value, norm));
        return false;
    }

    /* ignore other callbacks – device plug/unplug handled elsewhere */
    @Override public void connected   (Controller c) {}
    @Override public void disconnected(Controller c) {}
}
