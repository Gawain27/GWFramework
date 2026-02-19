package com.gwngames.core.base.init;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.util.ClassUtils;
import com.gwngames.starter.Platform;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Compliance test #4 – If multiple concrete implementations of the same
 * component interface exist at the same module level, each must declare a
 * specific {@link com.gwngames.starter.Platform platform}. This prevents ambiguous resolution
 * when two or more classes are eligible with identical priority.
 */
public class PlatformDifferentiationComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        // Build mapping: <Interface, Module> -> List of concrete impl classes
        Map<Class<?>, Map<String, List<Class<?>>>> index = new HashMap<>();

        for (Class<?> clazz : annotated) {
            if (clazz.isInterface()) continue; // ignore interfaces here
            Init ann = clazz.getAnnotation(Init.class);
            if (ann.module().equals(DefaultModule.INTERFACE)) continue; // skip component interfaces

            // Find the component interface this class implements (the first annotated one)
            Class<?> componentInterface = null;
            for (Class<?> iface : clazz.getInterfaces()) {
                Init ifaceAnn = iface.getAnnotation(Init.class);
                if (ifaceAnn != null && ifaceAnn.module().equals(DefaultModule.INTERFACE)) {
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
            Init ann = IClassLoader.resolvedInit(iface);
            if (ann.allowMultiple()) return; // multiple allowed
            log.debug("Interface {} – module {} – {} impls need unique platform or multi", iface.getSimpleName(), module, impls.size());

            for (Class<?> impl : impls) {
                String platform = impl.getAnnotation(Init.class).platform();
                Assertions.assertNotEquals(Platform.ALL, platform,
                    () -> String.format("%s and others [%s] implement %s at %s without platform differentiation or multi", impl.getName(), impls.stream().map(Class::getSimpleName).toList(), iface.getSimpleName(), module));
            }
        }));
    }
}
