package com.gwngames.core.base.cfg;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.cfg.BuildParameters;

@Init(module = ModuleNames.CORE)
public class CoreConfiguration extends BaseComponent implements IConfig {
    @Override
    public void registerParameters() {
        // Make sure param classes are loaded so keys auto-register globally (optional)
        // ParamUtils.ensureLoaded(BuildParameters.class);
        // TODO save and load param values, so set defaults will do nothing
        setDefault(BuildParameters.PROD_ENV, Boolean.FALSE); // TODO: set true
        setDefault(BuildParameters.DASHBOARD_PORT, 10_707);
    }
}
