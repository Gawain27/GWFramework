package com.gwngames.game.api.build;

import com.badlogic.gdx.ApplicationListener;
import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.GAME, module = DefaultModule.INTERFACE)
public interface IGameLauncher extends ApplicationListener, IBaseComp {
    String getLauncherName();
}
