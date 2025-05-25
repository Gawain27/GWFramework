package com.gwngames.core.init;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.SubComponentNames;
import org.junit.jupiter.api.Assertions;

/**
 * Verifies that every concrete class whose {@link Init#subComp()} ≠ NONE
 * implements <em>somewhere in its entire type hierarchy</em> at least one
 * interface annotated with {@code @Init(allowMultiple = true)}.
 */
final class SubComponentInterfaceAllowMultipleComplianceTest extends BaseTest {

    @Override
    protected void runTest() {

        ModuleClassLoader loader = ModuleClassLoader.getInstance();

        loader.getAnnotated(Init.class).forEach(concrete -> {
            Init ann = concrete.getAnnotation(Init.class);

            /* only interested in multi-sub-component concretes */
            if (concrete.isInterface()) return;
            if (ann == null || ann.subComp() == SubComponentNames.NONE) return;

            boolean ok = hasAllowMultipleInterface(concrete);

            String msg = "Class " + concrete.getName()
                + " defines subComp " + ann.subComp()
                + " but has no interface in its type hierarchy "
                + "annotated with @Init(allowMultiple = true)";

            log.log("Compliance - {}  → {}", concrete.getSimpleName(), ok ? "OK" : "FAIL");
            Assertions.assertTrue(ok, msg);
        });
    }

    /** Recursively scans super-classes and interfaces for the required marker. */
    private static boolean hasAllowMultipleInterface(Class<?> type) {
        if (type == null || type == Object.class) return false;

        /* Check all direct interfaces plus their super-interfaces. */
        for (Class<?> iface : type.getInterfaces()) {
            Init ifaceAnn = iface.getAnnotation(Init.class);
            if (ifaceAnn != null && ifaceAnn.allowMultiple()) return true;
            if (hasAllowMultipleInterface(iface)) return true;
        }
        /* Then walk up the superclass chain. */
        return hasAllowMultipleInterface(type.getSuperclass());
    }
}
