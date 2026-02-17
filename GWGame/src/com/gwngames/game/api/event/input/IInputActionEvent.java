package com.gwngames.game.api.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.action.IInputAction;
import com.gwngames.game.GameComponent;
import com.gwngames.game.GameSubComponent;

/**
 * A minimal input event whose sole purpose is to execute an {@link IInputAction}.
 * It carries the originating adapter and optional control identifier.
 */
@Init(component = GameComponent.INPUT_EVENT, subComp = GameSubComponent.INPUT_ACTION_EVENT)
public interface IInputActionEvent extends IInputEvent {

    /** Action to execute in the input queue. */
    void setAction(IInputAction action);
    void setControl(IInputIdentifier control);
}
