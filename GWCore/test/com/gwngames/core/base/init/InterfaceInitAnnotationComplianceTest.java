package com.gwngames.core.base.init;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Verifies that every interface extending {@link IBaseComp} is compliant
 * when considering inherited @Init attributes (module/component/platform).
 */
public class InterfaceInitAnnotationComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        int checked = 0;
        for (Class<?> c : annotated) {
            if (!c.isInterface()) continue;
            if (!IBaseComp.class.isAssignableFrom(c)) continue;

            // Use the merged/inherited view of @Init
            Init init = ModuleClassLoader.resolvedInit(c);

            Assertions.assertEquals(
                ModuleNames.INTERFACE, init.module(),
                c + " must inherit module=INTERFACE (directly or via parent)");

            Assertions.assertNotEquals(
                ComponentNames.NONE, init.component(),
                c + " must declare/inherit a non-NONE component");

            Assertions.assertEquals(
                PlatformNames.ALL, init.platform(),
                c + " must not override platform at interface level");

            checked++;
        }

        log.info("Interface annotation compliance – checked {} interfaces", checked);
    }
}
