package com.gwngames.game.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.GameComponent;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.IControllerAdapter;
import com.gwngames.game.api.input.IInputAdapterFactory;
import com.gwngames.game.api.input.IKeyboardAdapter;
import com.gwngames.game.api.input.ITouchAdapter;

/**
 * Default factory that builds adapters via IClassLoader and wires mandatory properties.
 */
@Init(module = GameModule.GAME)
public final class InputAdapterFactory extends BaseComponent implements IInputAdapterFactory {
    @Inject
    private IClassLoader loader;

    @Override
    public IKeyboardAdapter createKeyboard() {
        IKeyboardAdapter kb = loader.tryCreate(GameComponent.KEYBOARD_ADAPTER);
        if (kb == null) throw new IllegalStateException("No KEYBOARD_ADAPTER implementation found");
        return kb;
    }

    @Override
    public ITouchAdapter createTouch() {
        ITouchAdapter touch = loader.tryCreate(GameComponent.TOUCH_ADAPTER);
        if (touch == null) throw new IllegalStateException("No TOUCH_ADAPTER implementation found");
        return touch;
    }

    @Override
    public IControllerAdapter createController(Controller controller) {
        if (controller == null) throw new IllegalArgumentException("controller is null");
        IControllerAdapter ca = loader.tryCreate(GameComponent.CONTROLLER_ADAPTER);
        if (ca == null) throw new IllegalStateException("No CONTROLLER_ADAPTER implementation found");
        ca.setController(controller);
        return ca;
    }
}

