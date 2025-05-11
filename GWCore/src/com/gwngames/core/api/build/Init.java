package com.gwngames.core.api.build;

import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark entities that must be auto-created from the last level in the hierarchy<p>
 * This is necessary to be able to inject singleton objects into the game components, since such logic
 * should be decoupled from the initialization of objects
 *
 * @author samlam
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Init {
    ModuleNames module() default ModuleNames.UNIMPLEMENTED;
    ComponentNames component() default ComponentNames.NONE;
    PlatformNames platform() default PlatformNames.ALL;
    boolean allowMultiple() default false;
}
