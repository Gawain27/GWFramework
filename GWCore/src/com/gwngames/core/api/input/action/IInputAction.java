package com.gwngames.core.api.input.action;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * Something that can be triggered by an {@link IInputEvent} after the mapper
 * has translated a raw control into high-level intent.
 */
@FunctionalInterface
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_ACTION, allowMultiple = true, forceDefinition = true)
public interface IInputAction extends IBaseComp {
    void execute(IInputEvent event);
}
