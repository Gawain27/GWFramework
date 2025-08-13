package com.gwngames.core.api.input.buffer;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.input.InputContext;
import com.gwngames.core.data.ModuleNames;

import java.util.List;
import java.util.Set;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_CHAIN, allowMultiple = true, isEnum = true)
public interface IInputChain extends IBaseComp {

    /** Debug / logging name. */
    String name();

    /** Ordered list of simultaneous-press combos that form the chain. */
    List<IInputCombo> combos();

    /**
     * Contexts where the chain is allowed.
     * Return an empty set for “always visible”.
     */
    Set<InputContext> visibility();
}
