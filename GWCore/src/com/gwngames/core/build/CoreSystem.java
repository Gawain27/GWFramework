package com.gwngames.core.build;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.ISystem;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.cfg.BuildParameters;

@Init(module = ModuleNames.CORE)
public class CoreSystem extends BaseComponent implements ISystem {
    @Inject
    IConfig config;

    @Override
    public void adaptSystem() {
        FileLogger.enabled_level = config.get(BuildParameters.LOG_LEVEL);
    }

    @Override
    public void performChecks() {

    }

    @Override
    public void loadContext() {
        config.registerParameters();
    }
}
