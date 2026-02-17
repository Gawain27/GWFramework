package com.gwngames.game.api.input.buffer;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.input.ComboPriority;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.IInputIdentifier;

import java.util.Set;

@Init(module = DefaultModule.INTERFACE, component = GameComponent.INPUT_COMBO, allowMultiple = true, isEnum = true)
public interface IInputCombo extends IBaseComp {
    /** Identifiers pressed in the same frame. */
    Set<IInputIdentifier> identifiers();
    /** How many frames this combo stays “active” for lookup purposes. */
    int activeFrames();
    /** Defines how important the input is */
    ComboPriority priority();
    /** Convenience method since it's an enum*/
    String name();
}
