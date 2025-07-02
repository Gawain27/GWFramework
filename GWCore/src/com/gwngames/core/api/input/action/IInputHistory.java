package com.gwngames.core.api.input.action;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.Map;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_HISTORY, allowMultiple = true)
public interface IInputHistory extends IBaseComp {
    /* ───────── increment helpers ───────── */
    void record(IInputIdentifier id);

    void record(IInputCombo combo);

    void record(IInputChain chain);

    /* ───────── read-only views ─────────── */
    Map<IInputIdentifier,Long> identifiers();

    Map<IInputCombo,Long> combos();

    Map<IInputChain,Long> chains();

    void clear();
}
