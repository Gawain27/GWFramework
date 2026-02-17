package com.gwngames.game.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

/**
 * Factory for input adapters (keyboard, touch, controller).
 * All creations must go through the framework class loader.
 */
@Init(component = GameComponent.INPUT_ADAPTER_FACTORY, module = DefaultModule.INTERFACE)
public interface IInputAdapterFactory extends IBaseComp {

    /** Create a keyboard adapter and assign its slot. */
    IKeyboardAdapter  createKeyboard();

    /** Create a touch adapter and assign its slot. */
    ITouchAdapter createTouch();

    /**
     * Create a controller adapter, bind a concrete libGDX Controller, and assign its slot.
     * The adapter should reflect controller name/index in its getAdapterName() if desired.
     */
    IControllerAdapter createController(Controller controller);
}
