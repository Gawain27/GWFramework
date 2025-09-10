package com.gwngames.core.event.input;

import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.data.input.InputType;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.event.base.AbstractEvent;

public abstract class InputEvent extends AbstractEvent implements IInputEvent {
    private InputType type;
    private int slot;

    private long timestamp;
    protected IInputAction assignedAction;
    protected IInputAdapter adapter;

    @Override
    public IInputAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(IInputAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public InputType getType()       { return type; }
    @Override
    public int       getSlot()       { return slot; }
    @Override
    public long      getTimestamp()  { return timestamp; }
    @Override
    public IInputAction getAssignedAction() { return assignedAction; }
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    @Override
    public void setSlot(int slot) {
        this.slot = slot;
    }

    @Override
    public void setType(InputType type) {
        this.type = type;
    }
}
