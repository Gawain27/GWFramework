package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.event.input.IInputEvent;

@Init(component = GameComponent.INPUT_LISTENER, module = DefaultModule.INTERFACE, allowMultiple = true)
public interface IInputListener extends IBaseComp {
    /** Called whenever an InputEvent is dispatched. */
    void onInput(IInputEvent event);
    String identity();
}
