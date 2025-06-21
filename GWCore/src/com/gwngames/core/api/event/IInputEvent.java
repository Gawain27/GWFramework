package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * Common contract for every low-level input event (button, axis, touch, …).
 */
@Init(component = ComponentNames.INPUT_EVENT, module = ModuleNames.INTERFACE, allowMultiple = true, forceDefinition = true)
public interface IInputEvent extends IBaseComp {

    /** What kind of physical change happened (button down, axis move, …). */
    InputType getType();

    /** Which {@code IInputAdapter} slot (0…3) produced the event. */
    int getSlot();

    /** Event time in epoch-nanoseconds ( System.nanoTime() ). */
    long getTimestamp();
}
