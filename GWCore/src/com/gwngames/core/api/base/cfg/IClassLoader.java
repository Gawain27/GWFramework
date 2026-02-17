package com.gwngames.core.api.base.cfg;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Base class loader exposing methods for accessing and creating objects<br>
 * This instance is always framework-managed. by itself.
 * */
@Init(component = CoreComponent.CLASS_LOADER, module = DefaultModule.INTERFACE, external = true)
public interface IClassLoader extends IBaseComp {
    /* ==================================================================== */
    /*  Init-annotation inheritance helper                                  */
    /* ==================================================================== */

    /**
     * Produces a merged {@link Init} where missing {@code component()} or
     * {@code module()} values (sentinel {@link CoreComponent#NONE} /
     * {@link DefaultModule#UNIMPLEMENTED}) are inherited from the nearest superclass
     * or interface that declares them.
     * <p>
     * <strong>Note:</strong> The method now fails fast—if {@code clazz} lacks an
     * {@code @Init} annotation an {@link IllegalStateException} is thrown instead
     * of returning {@code null}.
     * </p>
     */
    static Init resolvedInit(Class<?> clazz) {
        Init base = clazz.getAnnotation(Init.class);
        if (base == null) {
            throw new IllegalStateException(
                "Class " + clazz.getName() + " is missing required @Init annotation");
        }

        boolean isEnum     = clazz.isEnum() || base.isEnum();
        boolean hasSubComp = !base.subComp().equals(CoreSubComponent.NONE);

        String comp   = base.component();
        String    module = base.module();

        // IMPORTANT: per policy, external is evaluated **only on the concrete class**.
        final boolean finalExternal = base.external();

        /* 1) Inherit from superclasses (full chain) for component/module and enum influence */
        Class<?> sup = clazz.getSuperclass();
        while (sup != null && sup != Object.class
            && (comp.equals(CoreComponent.NONE) || module.equals(DefaultModule.UNIMPLEMENTED))) {
            Init ann = sup.getAnnotation(Init.class);
            if (ann != null) {
                if (comp.equals(CoreComponent.NONE))       comp   = ann.component();
                if (module.equals(DefaultModule.UNIMPLEMENTED)) module = ann.module();
                if (!isEnum) isEnum = ann.isEnum();
            }
            sup = sup.getSuperclass();
        }

        /* Inherit from the entire interface graph (BFS) across the whole class chain */
        if (comp.equals(CoreComponent.NONE) || module.equals(DefaultModule.UNIMPLEMENTED)) {
            // Seed queue with all direct interfaces of clazz and all its superclasses
            Deque<Class<?>> q = new ArrayDeque<>();
            Set<Class<?>> visited = new HashSet<>();

            for (Class<?> cur = clazz; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                for (Class<?> ifc : cur.getInterfaces()) {
                    if (visited.add(ifc)) q.addLast(ifc);
                }
            }

            while (!q.isEmpty() && (comp.equals(CoreComponent.NONE) || module.equals(DefaultModule.UNIMPLEMENTED))) {
                Class<?> ifc = q.removeFirst();

                Init ann = ifc.getAnnotation(Init.class);
                if (ann != null) {
                    if (comp.equals(CoreComponent.NONE))       comp   = ann.component();
                    if (module.equals(DefaultModule.UNIMPLEMENTED)) module = ann.module();
                    if (!isEnum && ann.isEnum()) isEnum = true; // enum influence only
                    // NOTE: do not inherit `external` from interfaces
                }

                // Traverse super-interfaces too (this is the key difference)
                for (Class<?> parent : ifc.getInterfaces()) {
                    if (visited.add(parent)) q.addLast(parent);
                }
            }
        }

        // Recompute allowMultiple with potentially updated isEnum
        boolean finalAllowMult = base.allowMultiple() || isEnum || hasSubComp;

        String finalComp   = comp;
        String    finalModule = module;
        boolean        finalIsEnum = isEnum;

        return (Init) Proxy.newProxyInstance(
            Init.class.getClassLoader(),
            new Class<?>[]{Init.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "annotationType"        -> Init.class;
                case "component"             -> finalComp;
                case "module"                -> finalModule;
                case "subComp"               -> base.subComp();
                case "platform"              -> base.platform();
                case "allowMultiple"         -> finalAllowMult;
                case "isEnum"                -> finalIsEnum;
                case "isPlatformDependent"   -> base.isPlatformDependent();
                case "forceDefinition"       -> base.forceDefinition();
                case "external"              -> finalExternal; // concrete class only
                default                      -> method.invoke(base, args);
            });
    }

    List<Class<?>> scanForAnnotated(Class<? extends Annotation> ann);

    /* single-component (allowMultiple = false) --------------------------- */
    Class<?> findClass(String comp) throws ClassNotFoundException;

    List<Class<?>> findClasses(String comp) throws ClassNotFoundException;

    /* multi sub-components (allowMultiple = true) ------------------------ */
    Class<?> findSubComponent(String comp, String sub)
        throws ClassNotFoundException;

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT (one impl only)                                    */
    /* -------------------------------------------------------------------- */
    <T> T tryCreate(String comp, Object... args);

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT + SUBCOMPONENT                                     */
    /* -------------------------------------------------------------------- */
    <T> T tryCreate(String comp, String sub, Object... args);

    /* -------------------------------------------------------------------- */
    /*  ALL SUBCOMPONENTS (allowMultiple = true)                            */
    /* -------------------------------------------------------------------- */
    <T> List<T> tryCreateAll(String comp, Class<?> mustImplement, Object... args);

    /* -------------------------------------------------------------------- */
    /*  ALL SUBCOMPONENTS (allowMultiple = true)                            */
    /* -------------------------------------------------------------------- */
    <T> List<T> tryCreateAll(String comp, Object... args);

    /* -------------------------------------------------------------------- */
    /*  PLATFORM-SPECIFIC LOOK-UP                                           */
    /* -------------------------------------------------------------------- */
    <T> T tryCreateSpecific(String comp, String platform, Object... args);

    /* ==================================================================== */
    /*  Reflection convenience                                              */
    /* ==================================================================== */
    Object createInstance(Class<?> clazz, Object... params);

    <T extends IBaseComp> Optional<T> lookup(int id, Class<T> type);

    static String keyOf(String comp, String sub) {
        return comp + "#" + sub;
    }

    /**
     * Returns the closest lower-priority concrete implementation for the given
     * (component, sub) pair, relative to {@code currentPriority}.
     * <br><br>
     * Requires: ensureTypesLoaded() has populated allConcreteByKey with lists
     * sorted by modulePriority DESC (highest first).
     *
     * @throws ClassNotFoundException if no lower implementation exists.
     */
    Class<?> findNextLowerFor(String comp,
                              String sub,
                              int currentPriority) throws ClassNotFoundException;

    /**
     * @see #findNextLowerFor(String, String, int)
     * */
    Class<?> findNextLowerFor(String comp,
                              String sub,
                              String currentModule) throws ClassNotFoundException;

    /**
     * Finds the closest lower-priority concrete implementation for the SAME (component, subComp)
     * as the given class. For example, from NEEDLE_OF_SILVER (100) it will try GAME2D (15),
     * then CORE (5), etc., returning the first match.
     *
     * @param currentClass the higher-priority concrete class requesting a lower super
     * @return the Class<?> of the closest lower implementation
     * @throws ClassNotFoundException if no lower implementation exists
     * @throws IllegalStateException if currentClass is not a concrete @Init component
     */
    Class<?> findNextLowerFor(Class<?> currentClass) throws ClassNotFoundException;

    List<Class<?>> listSubComponents(String comp);

    List<Class<?>> listSubComponents(String comp, Class<?> mustImplement);
}
