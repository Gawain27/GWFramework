package com.gwngames.game.api.input.action;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.event.input.IInputEvent;

/**
 * Something that can be triggered by an {@link IInputEvent} after the mapper
 * has translated a raw control into high-level intent.
 */
@Init(module = DefaultModule.INTERFACE, component = GameComponent.INPUT_ACTION, allowMultiple = true, forceDefinition = true)
public interface IInputAction extends IBaseComp {
    void execute(IInputEvent event);
}
