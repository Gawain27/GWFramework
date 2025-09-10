package com.gwngames.core.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.input.IControllerAdapter;
import com.gwngames.core.api.input.IInputAdapterFactory;
import com.gwngames.core.api.input.IKeyboardAdapter;
import com.gwngames.core.api.input.ITouchAdapter;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * Default factory that builds adapters via IClassLoader and wires mandatory properties.
 */
@Init(module = ModuleNames.CORE)
public final class InputAdapterFactory extends BaseComponent implements IInputAdapterFactory {
    @Inject
    private IClassLoader loader;

    @Override
    public IKeyboardAdapter createKeyboard() {
        IKeyboardAdapter kb = loader.tryCreate(ComponentNames.KEYBOARD_ADAPTER);
        if (kb == null) throw new IllegalStateException("No KEYBOARD_ADAPTER implementation found");
        return kb;
    }

    @Override
    public ITouchAdapter createTouch() {
        ITouchAdapter touch = loader.tryCreate(ComponentNames.TOUCH_ADAPTER);
        if (touch == null) throw new IllegalStateException("No TOUCH_ADAPTER implementation found");
        return touch;
    }

    @Override
    public IControllerAdapter createController(Controller controller) {
        if (controller == null) throw new IllegalArgumentException("controller is null");
        IControllerAdapter ca = loader.tryCreate(ComponentNames.CONTROLLER_ADAPTER);
        if (ca == null) throw new IllegalStateException("No CONTROLLER_ADAPTER implementation found");
        ca.setController(controller);
        return ca;
    }
}

