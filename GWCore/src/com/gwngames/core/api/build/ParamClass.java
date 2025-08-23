package com.gwngames.core.api.build;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Marker that identifies classes meant to hold parameters.
 * @see com.gwngames.core.api.cfg.ParamKey
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(TYPE)
public @interface ParamClass {
}
