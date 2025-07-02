package com.gwngames.core.api.input.buffer;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.input.buffer.SmartInputBuffer;

import java.util.List;
import java.util.Optional;

/** Rolling history of per-frame combos for one adapter slot. */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_BUFFER)
public interface IInputBuffer extends IBaseComp {
    /** Called once per render step; builds a new combo from the frame’s input. */
    void nextFrame(long frame, List<IInputCombo> currentlyPressed);

    /** All combos currently in the buffer (oldest first). */
    List<IInputCombo> combos();

    /** Removes the first <code>count</code> combos (after a chain match). */
    void discard(int count);

    Optional<Entry> peekOldest();

    /** Public read-only view – combo plus its creation frame. */
    record Entry(IInputCombo combo, long frame) { }
}
