package com.gwngames.core.init;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Compliance test #4 – If multiple concrete implementations of the same
 * component interface exist at the same module level, each must declare a
 * specific {@link PlatformNames platform}. This prevents ambiguous resolution
 * when two or more classes are eligible with identical priority.
 */
public class PlatformDifferentiationComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        ModuleClassLoader loader = ModuleClassLoader.getInstance();

        // Collect every class annotated with @Init
        List<Class<?>> annotated = loader.getAnnotated(Init.class);

        // Build mapping: <Interface, Module> -> List of concrete impl classes
        Map<Class<?>, Map<ModuleNames, List<Class<?>>>> index = new HashMap<>();

        for (Class<?> clazz : annotated) {
            if (clazz.isInterface()) continue; // ignore interfaces here
            Init ann = clazz.getAnnotation(Init.class);
            if (ann.module() == ModuleNames.INTERFACE) continue; // skip component interfaces

            // Find the component interface this class implements (the first annotated one)
            Class<?> componentInterface = null;
            for (Class<?> iface : clazz.getInterfaces()) {
                Init ifaceAnn = iface.getAnnotation(Init.class);
                if (ifaceAnn != null && ifaceAnn.module() == ModuleNames.INTERFACE) {
                    componentInterface = iface;
                    break;
                }
            }
            if (componentInterface == null) {
                // Should not happen if previous compliance tests pass
                log.error("{} implements no @Init INTERFACE – skipping", clazz.getName());
                continue;
            }

            index
                .computeIfAbsent(componentInterface, k -> new HashMap<>())
                .computeIfAbsent(ann.module(), k -> new ArrayList<>())
                .add(clazz);
        }

        // Examine each <interface, module> bucket
        index.forEach((iface, byModule) -> byModule.forEach((module, impls) -> {
            if (impls.size() <= 1) return; // no ambiguity – nothing to check
            Init ann = iface.getAnnotation(Init.class);
            if (ann != null && ann.allowMultiple()) return; // multiple allowed
            log.debug("Interface {} – module {} – {} impls need unique platform or multi", iface.getSimpleName(), module, impls.size());

            for (Class<?> impl : impls) {
                PlatformNames platform = impl.getAnnotation(Init.class).platform();
                Assertions.assertNotEquals(PlatformNames.ALL, platform,
                    () -> String.format("%s and others implement %s at %s without platform differentiation or multi", impl.getName(), iface.getSimpleName(), module));
            }
        }));
    }
}
