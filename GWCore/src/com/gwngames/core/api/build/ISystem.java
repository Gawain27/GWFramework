package com.gwngames.core.api.build;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.build.check.StartupCheckImpl;
import com.gwngames.core.data.LogFiles;

import java.util.List;

/**
 * Denotes the system (e.g. the component that setups the whole game application)
 *
 * @author samlam
 * */
@Init(component = CoreComponent.SYSTEM, module = DefaultModule.INTERFACE)
public interface ISystem extends IBaseComp {
    FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    static List<StartupCheckImpl> getAllStartupChecks() {
        List<StartupCheckImpl> checks;
        ModuleClassLoader classLoader = ModuleClassLoader.getInstance();

        checks = classLoader.tryCreateAll(CoreComponent.STARTUP_CHECK);

        return checks;
    }

    void adaptSystem();

    void performChecks();

    void loadContext();

    default void setup(){
        log.info("Loading main context...");
        loadContext();
        log.info("Performing startup checks...");
        performChecks();

        log.info("Adapting System...");
        adaptSystem();
    }
}
