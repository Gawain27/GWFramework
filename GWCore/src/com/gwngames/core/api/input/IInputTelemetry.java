package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_TELEMETRY, module = ModuleNames.INTERFACE)
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
