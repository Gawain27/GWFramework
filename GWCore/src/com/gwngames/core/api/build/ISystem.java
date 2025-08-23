package com.gwngames.core.api.build;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.build.check.StartupCheckImpl;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;

import java.util.ArrayList;
import java.util.List;

/**
 * Denotes the system (e.g. the component that setups the whole game application)
 *
 * @author samlam
 * */
@Init(component = ComponentNames.SYSTEM, module = ModuleNames.INTERFACE)
public interface ISystem extends IBaseComp {
    FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    static List<StartupCheckImpl> getAllStartupChecks() {
        List<StartupCheckImpl> checks;
        ModuleClassLoader classLoader = ModuleClassLoader.getInstance();

        checks = classLoader.tryCreateAll(ComponentNames.STARTUP_CHECK);

        return checks;
    }


    void performChecks();

    void loadContext();

    default void setup(){
        loadContext();
        performChecks();
    }
}
