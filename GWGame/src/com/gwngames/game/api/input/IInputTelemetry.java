package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.buffer.IInputChain;
import com.gwngames.game.api.input.buffer.IInputCombo;

@Init(component = GameComponent.INPUT_TELEMETRY, module = DefaultModule.INTERFACE)
public interface IInputTelemetry extends IBaseComp {
    void pressed(IInputIdentifier id, long frame);
    void held   (IInputIdentifier id, long frame);
    void combo  (IInputCombo combo, long frame);
    void chain  (IInputChain chain, long frame);

    /** Already added earlier for buttons (default no-op kept here for context). */
    default void released(IInputIdentifier id, long frame) { /* no-op */ }

    /* ───── axis telemetry ───── */
    default void axis(IInputIdentifier id, float raw, float normalized, long frame) { /* no-op */ }

    /* ───── touch telemetry (libGDX-agnostic signature: x,y,pressure) ───── */
    default void touchDown(IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
    default void touchDrag(IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
    default void touchUp  (IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
}
