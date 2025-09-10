package com.gwngames.core.base.cfg;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.cfg.BuildParameters;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.data.input.InputParameters;

@Init(module = ModuleNames.CORE)
public class CoreConfiguration extends BaseComponent implements IConfig {
    @Override
    public void registerParameters() {
        // Make sure param classes are loaded so keys auto-register globally (optional)
        // ParamUtils.ensureLoaded(BuildParameters.class);
        // TODO save and load param values, so set defaults will do nothing
        setDefault(BuildParameters.PROD_ENV, Boolean.FALSE); // TODO: set true, currently false for ez dev
        setDefault(BuildParameters.LOG_LEVEL, FileLogger.DEBUG_LEVEL);
        setDefault(BuildParameters.DASHBOARD_PORT, 10_707);

        setDefault(InputParameters.COMBO_DEFAULT_TTL_FRAMES, 8);
        setDefault(InputParameters.INPUT_MAX_DEVICES, 4);
        setDefault(InputParameters.INPUT_DEVICE_POLLING, 15f);

        setDefault(EventParameters.COMM_EVENT_MAX_THREAD, 1);
        setDefault(EventParameters.RENDER_EVENT_MAX_THREAD, 8);
        setDefault(EventParameters.SYSTEM_EVENT_MAX_THREAD, 4);
        setDefault(EventParameters.LOGIC_EVENT_MAX_THREAD, 16);
        setDefault(EventParameters.INPUT_EVENT_MAX_THREAD, 64);
    }
}
