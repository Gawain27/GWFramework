package com.gwngames.core.api.build;

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
    boolean createNew() default false;
}
