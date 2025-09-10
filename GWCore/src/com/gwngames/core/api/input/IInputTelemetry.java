package com.gwngames.core.api.input;

import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;

public interface IInputTelemetry {
    void pressed(IInputIdentifier id, long frame);
    void held   (IInputIdentifier id, long frame);
    void combo  (IInputCombo combo, long frame);
    void chain  (IInputChain chain, long frame);

    /** Already added earlier for buttons (default no-op kept here for context). */
    default void released(IInputIdentifier id, long frame) { /* no-op */ }

    /* ───── NEW: axis telemetry ───── */
    default void axis(IInputIdentifier id, float raw, float normalized, long frame) { /* no-op */ }

    /* ───── NEW: touch telemetry (libGDX-agnostic signature: x,y,pressure) ───── */
    default void touchDown(IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
    default void touchDrag(IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
    default void touchUp  (IInputIdentifier id, float x, float y, float pressure, long frame) { /* no-op */ }
}
