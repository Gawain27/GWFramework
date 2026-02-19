package com.gwngames.game.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.IInputTelemetry;
import com.gwngames.game.api.input.action.IInputHistory;
import com.gwngames.game.api.input.buffer.IInputChain;
import com.gwngames.game.api.input.buffer.IInputCombo;

@Init(module = GameModule.GAME)
public class CoreInputTelemetry extends BaseComponent implements IInputTelemetry {
    @Inject
    protected IInputHistory history;

    @Override
    public void pressed(IInputIdentifier id, long frame) {
        if (history != null) history.record(id);
    }

    @Override
    public void held(IInputIdentifier id, long frame) {
        if (history != null) history.record(id);
    }

    @Override
    public void released(IInputIdentifier id, long frame) {
        if (history != null) history.release(id);
    }

    @Override
    public void combo(IInputCombo combo, long frame) {
        if (history != null) history.record(combo);
    }

    @Override
    public void chain(IInputChain chain, long frame) {
        if (history != null) history.record(chain);
    }

    /* just forward to history if you want counts; safe if history doesn’t implement them. */
    @Override
    public void axis(IInputIdentifier id, float raw, float norm, long frame) {
        if (history != null) history.axis(id, raw, norm);
    }

    @Override
    public void touchDown(IInputIdentifier id, float x, float y, float p, long frame) {
        if (history != null) history.touchDown(id, x, y, p);
    }

    @Override
    public void touchDrag(IInputIdentifier id, float x, float y, float p, long frame) {
        if (history != null) history.touchDrag(id, x, y, p);
    }

    @Override
    public void touchUp(IInputIdentifier id, float x, float y, float p, long frame) {
        if (history != null) history.touchUp(id, x, y, p);
    }
}
