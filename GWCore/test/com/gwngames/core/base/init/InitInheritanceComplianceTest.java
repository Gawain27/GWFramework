package com.gwngames.core.base.init;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Verifies the *inherit-and-merge* behaviour implemented in
 * {@link ModuleClassLoader#resolvedInit(Class)}
 * Rules:
 *   1. The merged annotation must never keep the sentinel values
 *      {@code component() == NONE}  or  {@code module() == UNIMPLEMENTED}.
 *   2. Interfaces must resolve to {@code module() == INTERFACE}.
 *   3. Concrete classes must resolve to a module *other* than INTERFACE.
 */
public final class InitInheritanceComplianceTest extends BaseTest {

    @Override
    protected void runTest() {

        /*  Discover every class (or interface) that carries @Init */
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        Assertions.assertFalse(annotated.isEmpty(),
            "No @Init classes discovered – class-path scanning failed?");

        /*  For each one, obtain the *MERGED* annotation via the loader */
        for (Class<?> type : annotated) {

            Init merged = ModuleClassLoader.resolvedInit(type);
            Assertions.assertNotNull(merged,
                () -> err("resolvedInit returned null", type));

            /* ---- Rule #1: no sentinel values may survive ---------------- */
            Assertions.assertNotEquals(ComponentNames.NONE, merged.component(),
                () -> err("component() still NONE", type));

            Assertions.assertNotEquals(ModuleNames.UNIMPLEMENTED, merged.module(),
                () -> err("module() still UNIMPLEMENTED", type));

            /* ---- Rule #2 & #3: INTERFACE only for interfaces ------------ */
            if (type.isInterface()) {
                Assertions.assertEquals(ModuleNames.INTERFACE, merged.module(),
                    () -> err("interfaces must resolve to module = INTERFACE", type));
            } else {
                Assertions.assertNotEquals(ModuleNames.INTERFACE, merged.module(),
                    () -> err("concrete classes must NOT resolve to INTERFACE", type));
            }
        }
    }

    /* Helper for concise assertion messages */
    private static String err(String msg, Class<?> c) {
        return "[InitInheritance] " + msg + " → " + c.getName();
    }
}
