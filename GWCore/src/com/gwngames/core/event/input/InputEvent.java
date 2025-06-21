package com.gwngames.core.event.input;

import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.event.base.AbstractEvent;

public abstract class InputEvent extends AbstractEvent implements IInputEvent {
    private final InputType type;
    private final int slot;
    private final long timestamp;

    protected InputEvent(InputType type, int slot, long timestamp) {
        this.type      = type;
        this.slot      = slot;
        this.timestamp = timestamp;
    }

    public InputType getType()       { return type; }
    public int       getSlot()       { return slot; }
    public long      getTimestamp()  { return timestamp; }
}
