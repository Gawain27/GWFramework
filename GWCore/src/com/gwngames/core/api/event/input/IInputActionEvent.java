package com.gwngames.core.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.SubComponentNames;

/**
 * A minimal input event whose sole purpose is to execute an {@link IInputAction}.
 * It carries the originating adapter and optional control identifier.
 */
@Init(component = ComponentNames.INPUT_EVENT, subComp = SubComponentNames.INPUT_ACTION_EVENT)
public interface IInputActionEvent extends IInputEvent {

    /** Action to execute in the input queue. */
    void setAction(IInputAction action);
    void setControl(IInputIdentifier control);
}
