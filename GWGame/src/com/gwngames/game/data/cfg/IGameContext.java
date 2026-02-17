package com.gwngames.game.data.cfg;

import com.badlogic.gdx.Application;
import com.gwngames.core.api.base.cfg.IContext;
import com.gwngames.core.data.cfg.ContextKey;

public interface IGameContext extends IContext {
    ContextKey<Application> APPLICATION = ContextKey.of("application", Application.class);
}
