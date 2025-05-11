package com.gwngames.core.base.cfg;


import com.gwngames.core.api.base.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE, component = ComponentNames.CONFIGURATION)
public class CoreConfiguration extends BaseComponent implements IConfig {
    @Override
    public void registerParameters() {

    }
}
