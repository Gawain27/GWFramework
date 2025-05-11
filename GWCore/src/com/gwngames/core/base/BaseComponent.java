package com.gwngames.core.base;


import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Contains basic functionalities for a game component
 *
 * @author samlam
 * */
public abstract class BaseComponent {
    static BaseComponent instance;

    @SuppressWarnings("unchecked")
    public static <T extends BaseComponent> T getInstance(Class<T> classType){
        if (instance == null) {
            Init init = classType.getAnnotation(Init.class);
            if (init == null)
                throw new IllegalStateException("Malformed component: " + classType.getSimpleName());
            instance = ModuleClassLoader.getInstance().tryCreate(init.component());

            if (instance == null)
                throw new IllegalStateException("No component found of class: " + classType.getSimpleName());

            List<Field> injectableFields = ClassUtils.getAnnotatedFields(classType, Inject.class);
            for(Field componentField : injectableFields){
                try {
                    Inject typeInject = componentField.getAnnotation(Inject.class);
                    Init typeInit = componentField.getType().getAnnotation(Init.class);

                    componentField.setAccessible(true);
                    if (typeInject.createNew()){
                        componentField.set(instance,
                            ModuleClassLoader.getInstance().tryCreate(
                                typeInit.component(), componentField.getType()));
                    } else {
                        componentField.set(instance, getInstance((Class<T>) componentField.getType()));
                    }
                    componentField.setAccessible(false);

                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return (T) instance;
    }


}
