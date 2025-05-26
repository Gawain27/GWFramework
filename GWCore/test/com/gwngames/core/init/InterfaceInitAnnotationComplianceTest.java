package com.gwngames.core.init;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Verifies that every interface extending {@link IBaseComp} is correctly annotated.
 * <ul>
 *     <li>{@code module} must be {@code ModuleNames.INTERFACE}</li>
 *     <li>{@code component} must be != {@code ComponentNames.NONE}</li>
 *     <li>{@code platform} must remain at its default ({@code PlatformNames.ALL})</li>
 * </ul>
 */
public class InterfaceInitAnnotationComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        int checked = 0;
        for (Class<?> c : annotated) {
            if (!c.isInterface()) continue;
            if (!IBaseComp.class.isAssignableFrom(c)) continue;

            Init ann = c.getAnnotation(Init.class);
            Assertions.assertNotNull(ann, c + " is missing @Init annotation");
            Assertions.assertEquals(ModuleNames.INTERFACE, ann.module(), c + " must declare module=INTERFACE");
            Assertions.assertNotEquals(ComponentNames.NONE, ann.component(), c + " must declare a component value");
            Assertions.assertEquals(PlatformNames.ALL, ann.platform(), c + " must not override platform");
            checked++;
        }

        log.log("Interface annotation compliance – checked {} interfaces", checked);
    }
}
