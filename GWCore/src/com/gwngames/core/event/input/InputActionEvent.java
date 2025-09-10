package com.gwngames.core.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IInputActionEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.input.InputType;

/**
 * Default implementation of {@link IInputActionEvent}.
 * Type is {@code InputType.ACTION}.
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.INPUT_ACTION_EVENT)
public class InputActionEvent extends InputEvent implements IInputActionEvent {

    private IInputAction action;
    private IInputIdentifier control; // optional

    @Override
    public void setAction(IInputAction action) { this.action = action; }

    @Override
    public IInputIdentifier getControl() { return control; }

    @Override
    public void setControl(IInputIdentifier id) { this.control = id; }


    @Override
    public String toString() {
        return "ACTION slot=" + getSlot() + " adapter=" + (getAdapter()==null?"null":getAdapter().getAdapterName())
            + " action=" + (action==null?"null":action.getClass().getSimpleName())
            + " ctrl=" + control
            + " ts=" + getTimestamp();
    }

    /** Convenience to stamp the type when created via setters. */
    public void markAsActionType() { setType(InputType.ACTION); }
}
