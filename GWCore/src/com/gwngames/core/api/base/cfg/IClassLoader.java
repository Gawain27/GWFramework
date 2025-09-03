package com.gwngames.core.api.base.cfg;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;
import com.gwngames.core.data.SubComponentNames;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

/**
 * Base class loader exposing methods for accessing and creating objects<br>
 * This instance is always framework-managed. by itself.
 * */
@Init(component = ComponentNames.CLASS_LOADER, module = ModuleNames.INTERFACE, external = true)
public interface IClassLoader extends IBaseComp {
    /* ==================================================================== */
    /*  Init-annotation inheritance helper                                  */
    /* ==================================================================== */

    /**
     * Produces a merged {@link Init} where missing {@code component()} or
     * {@code module()} values (sentinel {@link ComponentNames#NONE} /
     * {@link ModuleNames#UNIMPLEMENTED}) are inherited from the nearest superclass
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

        boolean isEnum       = clazz.isEnum() || base.isEnum();
        boolean hasSubComp   = base.subComp() != SubComponentNames.NONE;
        boolean ext = base.external();

        ComponentNames comp  = base.component();
        ModuleNames    module= base.module();

        // Policy: sub-component or enum implies allowMultiple = true
        boolean allowMult    = base.allowMultiple() || isEnum || hasSubComp;

        // IMPORTANT: external is evaluated **only on the concrete class**.
        final boolean externalFlag = base.external();

        /* inherit only component/module (and enum “influence”) */
        Class<?> sup = clazz.getSuperclass();
        while (sup != null && sup != Object.class &&
            (comp == ComponentNames.NONE || module == ModuleNames.UNIMPLEMENTED)) {

            Init ann = sup.getAnnotation(Init.class);
            if (ann != null) {
                if (comp   == ComponentNames.NONE)       comp   = ann.component();
                if (module == ModuleNames.UNIMPLEMENTED) module = ann.module();
                if (!isEnum) isEnum = ann.isEnum();
                if (!ext) ext = ann.external();
            }
            sup = sup.getSuperclass();
        }

        if (comp == ComponentNames.NONE || module == ModuleNames.UNIMPLEMENTED) {
            Class<?> cur = clazz;
            while (cur != null && cur != Object.class &&
                (comp == ComponentNames.NONE || module == ModuleNames.UNIMPLEMENTED)) {

                for (Class<?> ifc : cur.getInterfaces()) {
                    Init ann = ifc.getAnnotation(Init.class);
                    if (ann == null) continue;

                    if (comp   == ComponentNames.NONE)       comp   = ann.component();
                    if (module == ModuleNames.UNIMPLEMENTED) module = ann.module();

                    if (comp != ComponentNames.NONE && module != ModuleNames.UNIMPLEMENTED)
                        break;

                    if (ann.isEnum()) {
                        allowMult = true;
                        isEnum    = true;
                    }
                    if (ann.external())
                        ext = true;
                }
                cur = cur.getSuperclass();
            }
        }

        ComponentNames finalComp      = comp;
        ModuleNames    finalModule    = module;
        boolean        finalAllowMult = allowMult;
        boolean        finalIsEnum    = isEnum;
        boolean        finalExternal = ext;

        return (Init) Proxy.newProxyInstance(
            Init.class.getClassLoader(),
            new Class<?>[]{Init.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "annotationType" -> Init.class;
                case "component"      -> finalComp;
                case "module"         -> finalModule;
                case "subComp"        -> base.subComp();
                case "platform"       -> base.platform();
                case "allowMultiple"  -> finalAllowMult;
                case "isEnum"         -> finalIsEnum;
                case "isPlatformDependent" -> base.isPlatformDependent();
                case "forceDefinition"     -> base.forceDefinition();
                case "external"       -> finalExternal;
                default               -> method.invoke(base, args);
            });
    }

    List<Class<?>> scanForAnnotated(Class<? extends Annotation> ann);

    /* single-component (allowMultiple = false) --------------------------- */
    Class<?> findClass(ComponentNames comp) throws ClassNotFoundException;

    List<Class<?>> findClasses(ComponentNames comp) throws ClassNotFoundException;

    /* multi sub-components (allowMultiple = true) ------------------------ */
    Class<?> findSubComponent(ComponentNames comp, SubComponentNames sub)
        throws ClassNotFoundException;

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT (one impl only)                                    */
    /* -------------------------------------------------------------------- */
    <T> T tryCreate(ComponentNames comp, Object... args);

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT + SUBCOMPONENT                                     */
    /* -------------------------------------------------------------------- */
    <T> T tryCreate(ComponentNames comp, SubComponentNames sub, Object... args);

    /* -------------------------------------------------------------------- */
    /*  ALL SUBCOMPONENTS (allowMultiple = true)                            */
    /* -------------------------------------------------------------------- */
    <T> List<T> tryCreateAll(ComponentNames comp, Object... args);

    /* -------------------------------------------------------------------- */
    /*  PLATFORM-SPECIFIC LOOK-UP                                           */
    /* -------------------------------------------------------------------- */
    <T> T tryCreate(ComponentNames comp, PlatformNames platform, Object... args);

    /* ==================================================================== */
    /*  Reflection convenience                                              */
    /* ==================================================================== */
    Object createInstance(Class<?> clazz, Object... params);

    <T extends IBaseComp> Optional<T> lookup(int id, Class<T> type);
}
