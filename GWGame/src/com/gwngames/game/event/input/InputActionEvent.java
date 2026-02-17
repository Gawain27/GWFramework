package com.gwngames.game.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.input.InputType;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.event.input.IInputActionEvent;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.action.IInputAction;

/**
 * Default implementation of {@link IInputActionEvent}.
 * Type is {@code InputType.ACTION}.
 */
@Init(module = GameModule.GAME, subComp = GameSubComponent.INPUT_ACTION_EVENT)
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
