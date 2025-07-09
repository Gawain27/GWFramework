package com.gwngames.core.api.build;

import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.core.data.SubComponentNames;

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
    /** Specify the module (INTERFACE for interfaces) */
    ModuleNames module() default ModuleNames.UNIMPLEMENTED;
    /** Specify subcomponent id */
    ComponentNames component() default ComponentNames.NONE;
    /** Specifies the platform */
    PlatformNames platform() default PlatformNames.ALL;
    /** allows multiple concrete classes with same interface (still one instance per concrete class)*/
    boolean allowMultiple() default false;
    /** Signals that concrete component should specify the platform for it (ANDROID, WEB, etc.)*/
    boolean isPlatformDependent() default false;
    /** Signals that concrete classes are enums */
    boolean isEnum() default false;
    /** Signals that a component must define sub component */
    boolean forceDefinition() default false;
    /** Specifies the subcomponent id */
    SubComponentNames subComp() default SubComponentNames.NONE;
}


