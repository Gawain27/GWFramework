package com.gwngames.core.util;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.SubComponentNames;

import java.lang.reflect.*;
import java.util.Collections;
import java.util.List;

/**
 * A very minimal “CDI‐style” injector.
 * Call {@code Cdi.inject(myObject);} on any POJO that has fields annotated with {@link Inject}.
 *
 * Supports:
 *  - createNew: true/false (uses BaseComponent.getInstance(..., newInstance))
 *  - subComp: choose a specific sub-component
 *  - loadAll: List<T> with all available implementations (fully injected + post-injected)
 *  - post-injection hook: @PostInject void someMethod()
 */
public final class Cdi {

    private Cdi() {}

    @SuppressWarnings("unchecked")
    public static void inject(Object target) {
        if (target == null) return;

        // 1) Wire @Inject fields
        List<Field> fields = ClassUtils.getAnnotatedFields(target.getClass(), Inject.class);
        for (Field f : fields) {
            boolean restore = f.canAccess(target);
            try {
                f.setAccessible(true);
                Inject inj = f.getAnnotation(Inject.class);

                if (inj.loadAll()) {
                    // ===== List<T> injection (all implementations) =====
                    if (!List.class.isAssignableFrom(f.getType())) {
                        throw new IllegalStateException("@Inject(loadAll=true) field must be a List: " + f);
                    }

                    Class<?> elemType = resolveListElementType(f);
                    if (!IBaseComp.class.isAssignableFrom(elemType)) {
                        throw new IllegalStateException("List element type must implement IBaseComp: " + f);
                    }

                    Init meta = elemType.getAnnotation(Init.class);
                    if (meta == null || !meta.allowMultiple()) {
                        throw new IllegalStateException("Component does not allow multiple: " + elemType.getName());
                    }

                    // Create all implementations; ensure they are injected as well
                    List<?> all = ModuleClassLoader.getInstance().tryCreateAll(meta.component());
                    for (Object o : all) {
                        // recursively inject dependencies + post-inject on each element
                        Cdi.inject(o);
                    }
                    f.set(target, Collections.unmodifiableList(all));
                    continue;
                }

                // ===== Single injection (singleton or fresh) =====
                Class<?> fieldType = f.getType();
                if (!IBaseComp.class.isAssignableFrom(fieldType)) {
                    throw new IllegalStateException("@Inject on non-IBaseComp field: " + f);
                }

                SubComponentNames sub = inj.subComp();
                boolean newInstance = inj.createNew();

                Object wired;
                if (sub == SubComponentNames.NONE) {
                    // interface/class default impl
                    wired = newInstance
                        ? BaseComponent.getInstance((Class<? extends IBaseComp>) fieldType, true)
                        : BaseComponent.getInstance((Class<? extends IBaseComp>) fieldType);
                } else {
                    // specific sub-component
                    wired = newInstance
                        ? BaseComponent.getInstance((Class<? extends IBaseComp>) fieldType, sub, true)
                        : BaseComponent.getInstance((Class<? extends IBaseComp>) fieldType, sub);
                }

                f.set(target, wired);

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Injection failed for field " + f + " on " + target.getClass().getName(), e);
            } finally {
                f.setAccessible(restore);
            }
        }

        // 2) Run @PostInject hooks (void, no-arg) after all fields are wired
        runPostInject(target);
    }

    private static void runPostInject(Object target) {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(PostInject.class)) continue;
                if (m.getParameterCount() != 0 || m.getReturnType() != void.class) {
                    throw new IllegalStateException("@PostInject method must be void and no-arg: " + m);
                }
                boolean restore = m.canAccess(target);
                try {
                    m.setAccessible(true);
                    m.invoke(target);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to invoke @PostInject: " + m, e);
                } finally {
                    m.setAccessible(restore);
                }
            }
            c = c.getSuperclass();
        }
    }

    private static Class<?> resolveListElementType(Field listField) {
        Type g = listField.getGenericType();
        if (g instanceof ParameterizedType pt) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof Class<?> c) return c;
        }
        throw new IllegalStateException("Cannot resolve List element type for " + listField);
    }
}
