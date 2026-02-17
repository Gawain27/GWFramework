package com.gwngames.game.input.adapter;

import com.badlogic.gdx.controllers.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.data.input.IdentifierDefinition;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.IAxisIdentifier;
import com.gwngames.game.api.input.IButtonIdentifier;
import com.gwngames.game.api.input.IControllerAdapter;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.input.BaseInputAdapter;

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
@Init(module = GameModule.GAME)
public final class ControllerInputAdapter extends BaseInputAdapter implements IControllerAdapter, ControllerListener {

    private static final float DEAD_ZONE = 0.10f;

    private Controller controller;

    /* one identifier instance per (button / axis) ---------------------- */
    private final Map<Integer, IButtonIdentifier> btnId = new HashMap<>();
    private final Map<Integer, IAxisIdentifier> axisId = new HashMap<>();

    public ControllerInputAdapter() {
        /* ── build the lookup caches once ─────────────────────────── */
        for (IdentifierDefinition def : IdentifierDefinition.values()) {
            for (IInputIdentifier raw : def.ids()) {

                /* buttons */
                if (raw instanceof IButtonIdentifier tmpl && tmpl.getButtonCode() >= 0) {
                    IButtonIdentifier id = loader.tryCreate(GameComponent.CONTROLLER_BUTTON_INPUT);
                    id.setRecordWhilePressed(tmpl.isRecordWhilePressed());
                    id.setButtonCode(tmpl.getButtonCode());
                    btnId.putIfAbsent(tmpl.getButtonCode(), id);
                }

                /* axes */
                if (raw instanceof IAxisIdentifier tmpl) {
                    IAxisIdentifier id = loader.tryCreate(GameComponent.CONTROLLER_AXIS_INPUT);
                    id.setAxisCode(tmpl.getAxisCode());
                    id.setRecordWhilePressed(tmpl.isRecordWhilePressed());
                    axisId.putIfAbsent(tmpl.getAxisCode(), id);
                }
            }
        }
    }
    /* ─────────────────────────── ctor & life-cycle ──────────────────── */

    @Override
    public Controller getController() { return controller; }

    @Override public void start() { Controllers.addListener(this); }
    @Override public void stop()  { Controllers.removeListener(this); }

    @Override
    public String getAdapterName() {
        if (getController() != null) {
            return getController().getName();
        }
        return "Unknown";
    }

    /* ───────────────────── ControllerListener callbacks ────────────── */
    @Override
    public boolean buttonDown(Controller c, int buttonCode) {
        if (c != controller) return false;
        IButtonIdentifier id = btnId.get(buttonCode);
        if (id == null) return false;               // unmapped button
        inputManager.emitButtonDown(this, id, 1f);
        return false;
    }

    @Override
    public boolean buttonUp(Controller c, int buttonCode) {
        if (c != controller) return false;
        IButtonIdentifier id = btnId.get(buttonCode);
        if (id == null) return false;
        inputManager.emitButtonUp(this, id, 0f);
        return false;
    }

    @Override
    public boolean axisMoved(Controller c, int axisCode, float value) {
        if (c != controller) return false;
        IAxisIdentifier id = axisId.get(axisCode);
        if (id == null) return false;               // unmapped axis
        float norm = Math.abs(value) < DEAD_ZONE ? 0f : value;
        inputManager.emitAxis(this, id, value, norm);
        return false;
    }

    /* ignore other callbacks – device plug/unplug handled elsewhere */
    @Override public void connected   (Controller c) {}
    @Override public void disconnected(Controller c) {}

    @Override
    public void setController(Controller controller) {
        this.controller = controller;

        if (controller == null) return;
        axisId.values().forEach(e -> e.setController(controller));
        btnId.values().forEach(e -> e.setController(controller));
    }
}
