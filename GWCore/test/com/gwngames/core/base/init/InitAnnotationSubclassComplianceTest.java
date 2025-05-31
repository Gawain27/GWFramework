package com.gwngames.core.base.init;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Compliance test #2 – every class annotated with {@link Init}
 * must implement the marker interface {@link IBaseComp}.
 */
public class InitAnnotationSubclassComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        List<Class<?>> annotated = ClassUtils.getAnnotatedClasses(Init.class);

        annotated.forEach(c ->
            Assertions.assertTrue(
                IBaseComp.class.isAssignableFrom(c),
                () -> "@Init‑annotated class " + c.getName() + " does not implement IBaseComp")
        );

        log.info("Validated {} @Init‑annotated classes implement IBaseComp", annotated.size());
    }
}

