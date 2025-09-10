package com.gwngames.core.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.input.InputType;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.data.ComponentNames;

/**
 * Common contract for every low-level input event (button, axis, touch, …).
 */
@Init(component = ComponentNames.INPUT_EVENT)
public interface IInputEvent extends IEvent {

    /** What kind of physical change happened (button down, axis move, …). */
    InputType getType();
    IInputIdentifier getControl();

    /** Which {@code IInputAdapter} slot (0…3) produced the event. */
    int getSlot();

    /** Event time in epoch-nanoseconds ( System.nanoTime() ). */
    long getTimestamp();

    /** The action assigned at runtime to the input type */
    IInputAction getAssignedAction();

    void setTimestamp(long timestamp);

    void setSlot(int slot);

    void setType(InputType type);

    /** Originating adapter (required). */
    IInputAdapter getAdapter();
    void setAdapter(IInputAdapter adapter);
}
