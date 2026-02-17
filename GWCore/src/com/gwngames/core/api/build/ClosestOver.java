package com.gwngames.core.api.build;

import com.gwngames.core.CoreSubComponent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS) // visible in bytecode scan
public @interface ClosestOver {
    String component();
    String sub() default CoreSubComponent.NONE;
}
