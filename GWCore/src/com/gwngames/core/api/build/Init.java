package com.gwngames.core.api.build;

import com.gwngames.core.CoreComponent;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.starter.Platform;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Init {
    /** The module (INTERFACE for interfaces). */
    ModuleNames module() default ModuleNames.UNIMPLEMENTED;

    /** Component this class/interface belongs to. */
    String component() default CoreComponent.NONE;

    /** Platform hint (interfaces typically ALL). */
    String platform() default Platform.ALL;

    /** Whether the interface admits multiple concrete implementations. */
    boolean allowMultiple() default false;

    /** If true, concrete component is platform-dependent. */
    boolean isPlatformDependent() default false;

    /** If true, the concrete class is an enum. */
    boolean isEnum() default false;

    /** If true, a sub-component must be specified. */
    boolean forceDefinition() default false;

    /** Sub-component id (if any). */
    String subComp() default CoreSubComponent.NONE;

    /**
     * If true on a **concrete class**, the instance must be obtained via
     * a public static no-arg {@code getInstance()} method on that class.
     * This flag is **only** read from the concrete class' own annotation
     * (not inherited from ancestors).
     */
    boolean external() default false;
}
