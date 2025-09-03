package com.gwngames.core.base.init;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Ensures that @Init meta-data on interfaces / implementations
 * follows GWFramework rules (module = INTERFACE, allowMultiple+forceDefinition
 * ⇒ every concrete impl has subComp ≠ NONE).
 */
public class SubComponentForceDefinitionTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();                              // LibGDX stubs

        // Load every component
        ModuleClassLoader mcl = ModuleClassLoader.getInstance();

        /* reflect private static lists already filled by the loader */
        Field fIf  = ModuleClassLoader.class.getDeclaredField("interfaceTypes");
        Field fCon = ModuleClassLoader.class.getDeclaredField("concreteTypes");
        fIf .setAccessible(true);
        fCon.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Class<?>> interfaces = (List<Class<?>>) fIf .get(null);
        @SuppressWarnings("unchecked")
        List<Class<?>> concretes  = (List<Class<?>>) fCon.get(null);

        /* ---------------- Rule #1 : interface ⇄ annotation consistency --- */
        for (Class<?> iface : interfaces) {
            Init an = IClassLoader.resolvedInit(iface);

            boolean extendsBase = IBaseComp.class.isAssignableFrom(iface);

            // if interface extends IBaseComp  -> module must be INTERFACE
            if (extendsBase) {
                Assertions.assertEquals(ModuleNames.INTERFACE, an.module(),
                    "Interface "+iface.getSimpleName()+" extends IBaseComp but module!=INTERFACE");
            }

            // if annotation says INTERFACE    -> interface must extend IBaseComp
            if (an.module() == ModuleNames.INTERFACE) {
                Assertions.assertTrue(extendsBase,
                    "Interface "+iface.getSimpleName()+" has module=INTERFACE but does not extend IBaseComp");
            }
        }

        /* ---------------- Rule #2 : allowMultiple+forceDefinition --------- */
        for (Class<?> iface : interfaces) {
            Init an = IClassLoader.resolvedInit(iface);
            if (!an.allowMultiple() || !an.forceDefinition()) continue;

            /* gather concrete, non-abstract impls of this interface */
            List<Class<?>> impls = concretes.stream()
                .filter(iface::isAssignableFrom)
                .filter(c -> !c.isInterface())
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .toList();

            for (Class<?> impl : impls) {
                Init implAnn = IClassLoader.resolvedInit(impl);
                Assertions.assertNotNull(implAnn,
                    "Concrete "+impl.getSimpleName()+" missing @Init");

                Assertions.assertNotEquals(SubComponentNames.NONE, implAnn.subComp(),
                    "Concrete "+impl.getSimpleName()+" must define subComp (interface "
                        +iface.getSimpleName()+")");
            }
        }
    }
}
