package com.gwngames.core.base.cfg;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.cfg.BuildParameters;
import com.gwngames.core.data.event.EventParameters;

@Init(module = CoreModule.CORE)
public class CoreConfiguration extends BaseComponent implements IConfig {
    @Override
    public void registerParameters() {
        // Make sure param classes are loaded so keys auto-register globally (optional)
        // ParamUtils.ensureLoaded(BuildParameters.class);
        // TODO save and load param values, so set defaults will do nothing
        setDefault(BuildParameters.PROD_ENV, Boolean.FALSE); // TODO: set true, currently false for ez dev
        setDefault(BuildParameters.LOG_LEVEL, FileLogger.DEBUG_LEVEL);
        setDefault(BuildParameters.DASHBOARD_PORT, 10_707);

        setDefault(EventParameters.COMM_EVENT_MAX_THREAD, 1);
        setDefault(EventParameters.SYSTEM_EVENT_MAX_THREAD, 4);
    }
}
