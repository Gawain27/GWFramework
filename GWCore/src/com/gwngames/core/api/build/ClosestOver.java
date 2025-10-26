package com.gwngames.core.api.build;

import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.SubComponentNames;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS) // visible in bytecode scan
public @interface ClosestOver {
    ComponentNames component();
    SubComponentNames sub() default SubComponentNames.NONE;
}
