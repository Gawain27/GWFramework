package com.gwngames.starter.build;

import com.badlogic.gdx.Application;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.LAUNCHER, module = ModuleNames.INTERFACE)
public interface ILauncher extends IBaseComp {
    Application createApplication();
    String getVersion();
}
