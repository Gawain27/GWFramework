package com.gwngames.core.api.input.buffer;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.Set;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_COMBO, allowMultiple = true, isEnum = true)
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
