package com.gwngames.core.base;


import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains basic functionalities for a game component
 *
 * @author samlam
 * */
public abstract class BaseComponent implements IBaseComp{
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final Map<Class<? extends IBaseComp>, IBaseComp> INSTANCES
        = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> classType) {
        log.debug("Looking up: {}", classType.getSimpleName());
        return (T) INSTANCES.computeIfAbsent(classType, BaseComponent::createAndInject);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IBaseComp> T createAndInject(Class<T> classType) {
        Init init = classType.getAnnotation(Init.class);
        if (init == null)
            throw new IllegalStateException("Malformed component: " + classType.getSimpleName());

        T instance = ModuleClassLoader.getInstance().tryCreate(init.component());
        if (instance == null)
            throw new IllegalStateException("No component found of class: " + classType.getSimpleName());

        List<Field> injectableFields = ClassUtils.getAnnotatedFields(instance.getClass(), Inject.class);
        for (Field field : injectableFields) {
            try {
                field.setAccessible(true);
                Inject inject = field.getAnnotation(Inject.class);

                if (inject.createNew()) {
                    Init fieldInit = field.getType().getAnnotation(Init.class);
                    field.set(instance, ModuleClassLoader.getInstance()
                        .tryCreate(fieldInit.component(), field.getType()));
                } else {
                    field.set(instance, getInstance((Class<? extends BaseComponent>) field.getType()));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                field.setAccessible(false);
            }
        }
        return instance;
    }
}
