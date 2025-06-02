package com.gwngames.core.util;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;

import java.lang.reflect.Field;
import java.util.List;

/**
 * A very minimal “CDI‐style” injector.  Call
 *     Cdi.inject(myObject);
 * on any POJO that has fields annotated with {@link Inject}.
 */
public class Cdi {

    /**
     * Scan 'injectTo' for fields annotated @Inject, then either:
     *  - if inject.createNew() == true:
     *       • look up the field’s type for an @Init(...) annotation
     *       • ask ModuleClassLoader to create a new instance of that component
     *  - otherwise:
     *       • assume the field’s type extends BaseComponent (implements IBaseComp)
     *       • ask BaseComponent.getInstance(...) for the singleton
     */
    @SuppressWarnings("unchecked")
    public static void inject(Object injectTo) {
        if (injectTo == null) return;

        // Find every field in injectTo’s class (and superclasses) marked @Inject:
        List<Field> fields = ClassUtils.getAnnotatedFields(injectTo.getClass(), Inject.class);

        for (Field f : fields) {
            boolean wasAccessible = f.canAccess(injectTo);
            try {
                f.setAccessible(true);
                Inject annotation = f.getAnnotation(Inject.class);

                Class<?> fieldType = f.getType();

                Object valueToInject;
                if (annotation.createNew()) {
                    // 1) If createNew == true, we must create a fresh instance via ModuleClassLoader.
                    //    We expect the fieldType to be annotated with @Init(component="…")
                    Init initMeta = fieldType.getAnnotation(Init.class);
                    if (initMeta == null) {
                        throw new IllegalStateException(
                            "@Inject(createNew=true) found on field “" + f.getName() +
                                "” of “" + injectTo.getClass().getSimpleName() +
                                "” but “" + fieldType.getSimpleName() + "” is not annotated @Init"
                        );
                    }
                    // Ask ModuleClassLoader to create a brand‐new instance of that component
                    valueToInject = ModuleClassLoader
                        .getInstance()
                        .tryCreate(initMeta.component(), fieldType);

                    if (valueToInject == null) {
                        throw new IllegalStateException(
                            "ModuleClassLoader could not create a new instance of “" +
                                fieldType.getSimpleName() + "” (component='" + initMeta.component() + "')"
                        );
                    }
                } else {
                    // 2) If createNew == false, we expect fieldType to be a subclass of BaseComponent.
                    if (!IBaseComp.class.isAssignableFrom(fieldType)) {
                        throw new IllegalStateException(
                            "@Inject(createNew=false) found on field “" + f.getName() +
                                "” of “" + injectTo.getClass().getSimpleName() +
                                "” but “" + fieldType.getSimpleName() + "” does not extend BaseComponent"
                        );
                    }
                    //noinspection unchecked
                    Class<? extends IBaseComp> componentClass = (Class<? extends IBaseComp>) fieldType;
                    valueToInject = BaseComponent.getInstance(componentClass);
                }

                f.set(injectTo, valueToInject);

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject field “" + f.getName()
                    + "” into instance of “" + injectTo.getClass().getSimpleName() + "”", e);
            } finally {
                f.setAccessible(wasAccessible);
            }
        }
    }
}
