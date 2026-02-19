package com.gwngames.core.api.plugin;

import com.gwngames.core.api.base.cfg.IApplicationLogger;
import com.gwngames.core.api.build.IPlugin;

public interface LoggingPlugin extends IPlugin {
    IApplicationLogger createApplicationLogger();
}
