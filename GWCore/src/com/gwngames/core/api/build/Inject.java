package com.gwngames.core.api.build;

import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.IBaseComp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used in game components to inject an existing or new object into the class.<p>
 * This is especially useful when we want to override some behaviour in a lower level module,
 * where access is restricted.
 *
 * @author samlam
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
    /** If true (and allowMultiple must be true!) then inject all concrete components into the list field */
    boolean loadAll() default false;
    Class<? extends IBaseComp> subTypeOf() default IBaseComp.class;
    /** If true, create a new temporary comp every time annotated component is injected */
    boolean createNew() default false;
    /** Specify the sub component, if component has multiple */
    String subComp() default CoreSubComponent.NONE;
}
