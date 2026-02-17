package com.gwngames.starter.build;

import com.badlogic.gdx.Application;
import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

@Init(component = CoreComponent.LAUNCHER, module = DefaultModule.INTERFACE)
public interface ILauncher extends IBaseComp {
    Application createApplication();
    String getVersion();
}
