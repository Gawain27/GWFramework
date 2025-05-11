package com.gwngames.core.build;

import com.gwngames.core.api.base.IConfig;
import com.gwngames.core.api.build.ISystem;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE, component = ComponentNames.SYSTEM)
public class CoreSystem extends BaseComponent implements ISystem {
    @Inject
    IConfig config;

    @Override
    public void performChecks() {

    }

    @Override
    public void loadContext() {
        config.registerParameters();
    }
}
