package com.gwngames.core.api.build;

import com.badlogic.gdx.ApplicationListener;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.GAME, module = ModuleNames.INTERFACE)
public interface IGameLauncher extends ApplicationListener, IBaseComp {
}
