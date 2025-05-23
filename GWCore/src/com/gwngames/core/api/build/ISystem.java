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
 * Denotes a system (e.g. the component that setups the whole game application)
 *
 * @author samlam
 * */
@Init(component = ComponentNames.SYSTEM, module = ModuleNames.INTERFACE)
public interface ISystem extends IBaseComp {
    FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    static List<StartupCheckImpl> getAllStartupChecks() {
        List<StartupCheckImpl> checks = new ArrayList<>();
        ModuleClassLoader classLoader = ModuleClassLoader.getInstance();
        List<Class<?>> startupCheckClasses = classLoader.getAnnotated(StartupCheck.class);

        // Filter: valid subclasses of StartupCheckImpl (excluding base class itself)
        List<Class<?>> validSubclasses = new ArrayList<>();
        for (Class<?> clazz : startupCheckClasses) {
            if (StartupCheckImpl.class.isAssignableFrom(clazz) && !clazz.equals(StartupCheckImpl.class)) {
                validSubclasses.add(clazz);
            }
        }

        // Identify leaf subclasses (no other discovered class extends them)
        List<Class<?>> leafSubclasses = new ArrayList<>();
        for (Class<?> candidate : validSubclasses) {
            boolean isExtended = false;
            for (Class<?> other : validSubclasses) {
                if (candidate != other && candidate.isAssignableFrom(other)) {
                    isExtended = true;
                    break;
                }
            }
            if (!isExtended) {
                leafSubclasses.add(candidate);
            }
        }

        // Instantiate only the leaf subclasses
        for (Class<?> leafClass : leafSubclasses) {
            try {
                checks.add((StartupCheckImpl) classLoader.createInstance(leafClass));
            } catch (Exception e) {
                log.error("Failed to instantiate {}: {}", leafClass.getSimpleName(), e.getMessage());
            }
        }

        return checks;
    }


    void performChecks();

    void loadContext();

    default void setup(){
        loadContext();
        performChecks();
    }
}
