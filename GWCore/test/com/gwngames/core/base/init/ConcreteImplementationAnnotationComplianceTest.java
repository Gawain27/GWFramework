package com.gwngames.core.base.init;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Verifies that each concrete implementation of an {@link IBaseComp} interface
 * complies with annotation rules:
 * <ul>
 *     <li>Must carry an {@link Init} annotation.</li>
 *     <li>{@link Init#component()} must be <em>unset</em> (i.e. {@code NONE}).</li>
 *     <li>{@link Init#module()} cannot be {@link DefaultModule#INTERFACE}.</li>
 * </ul>
 */
public class ConcreteImplementationAnnotationComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        // Build a map: interface -> concrete implementations
        Map<Class<?>, List<Class<?>>> ifaceImpls = new HashMap<>();
        for (Class<?> c : annotated) {
            if (!IBaseComp.class.isAssignableFrom(c)) continue; // skip unrelated
            Init ann = c.getAnnotation(Init.class);
            if (c.isInterface() && ann.module().equals(DefaultModule.INTERFACE)) {
                // ensure a list entry
                ifaceImpls.computeIfAbsent(c, k -> new ArrayList<>());
            }
        }

        // find concrete implementations (may or may not be annotated yet)
        for (Class<?> c : annotated) {
            if (c.isInterface()) continue;
            // determine the interface it implements among collected
            for (Class<?> iface : ifaceImpls.keySet()) {
                if (iface.isAssignableFrom(c)) {
                    ifaceImpls.get(iface).add(c);
                }
            }
        }

        // Validate each concrete class
        for (List<Class<?>> implList : ifaceImpls.values()) {
            for (Class<?> impl : implList) {
                log.debug("Checking {}", impl.getName());
                Assertions.assertFalse(impl.isInterface(), impl.getName() + " is unexpectedly an interface");
                Assertions.assertFalse(Modifier.isAbstract(impl.getModifiers()), impl.getName() + " is abstract");

                Init ann = impl.getAnnotation(Init.class);
                Assertions.assertNotNull(ann, impl.getName() + " must be annotated with @Init");
                Assertions.assertEquals(CoreComponent.NONE, ann.component(),
                    impl.getName() + ": concrete classes must not set component()");
                Assertions.assertNotEquals(DefaultModule.INTERFACE, ann.module(),
                    impl.getName() + " must not declare module() = INTERFACE");
            }
        }
    }
}

