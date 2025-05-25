package com.gwngames.core.init;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Compliance test #2 – every class annotated with {@link Init}
 * must implement the marker interface {@link IBaseComp}.
 */
public class InitAnnotationSubclassComplianceTest extends BaseTest {

    @Override
    protected void runTest() {
        ModuleClassLoader loader = ModuleClassLoader.getInstance();
        List<Class<?>> annotated = loader.getAnnotated(Init.class);

        annotated.forEach(c ->
            Assertions.assertTrue(
                IBaseComp.class.isAssignableFrom(c),
                () -> "@Init‑annotated class " + c.getName() + " does not implement IBaseComp")
        );

        log.log("Validated {} @Init‑annotated classes implement IBaseComp", annotated.size());
    }
}

