package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.base.log.LogBus;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.util.ClassUtils;
import com.gwngames.core.util.ComponentUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Root-class for every non-enum GW component.
 */
public abstract class BaseComponent implements IBaseComp {

    /** Per-instance id (assigned once). */
    private final int multId = ComponentUtils.assign(this);

    /** System logger (subclasses may also use their own FileLogger). */
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    /** One cached instance for each Component + SubComp pair. */
    private static final Map<String, IBaseComp> INSTANCES = new ConcurrentHashMap<>();

    /* ───────────────────── Log helpers (also feed LogBus) ───────────────────── */

    protected void logInfo(String msg, Object... args) {
        LOG.info(msg, args);
        LogBus.record(dashboardKey(), LogBus.Level.INFO, String.format(msg, args), null);
    }
    protected void logDebug(String msg, Object... args) {
        LOG.debug(msg, args);
        LogBus.record(dashboardKey(), LogBus.Level.DEBUG, String.format(msg, args), null);
    }
    protected void logError(String msg, Object... args) {
        LOG.error(msg, args);
        LogBus.record(dashboardKey(), LogBus.Level.ERROR, String.format(msg, args), null);
    }
    protected void logError(String msg, Throwable ex, Object... args) {
        LOG.error(msg, ex, args);
        LogBus.record(dashboardKey(), LogBus.Level.ERROR, String.format(msg, args), ex);
    }

    @Override public void setMultId(int newId) { /* regular comps ignore external set */ }

    /* ===================== Public lookup helpers ===================== */

    public static <T extends IBaseComp> T getInstance(Class<T> type) {
        return getInstance(type, SubComponentNames.NONE, false);
    }
    public static <T extends IBaseComp> T getInstance(Class<T> type, SubComponentNames sub) {
        return getInstance(type, sub, false);
    }
    public static <T extends IBaseComp> T getInstance(Class<T> type, boolean fresh) {
        return getInstance(type, SubComponentNames.NONE, fresh);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> type,
                                                      SubComponentNames sub,
                                                      boolean fresh) {
        if (!fresh) {
            return (T) INSTANCES.computeIfAbsent(cacheKey(type, sub),
                k -> createAndInject(type, sub));
        }
        return createAndInject(type, sub); // fresh, not cached
    }

    /* ================= instantiation & @Inject wiring ================= */

    private static <T extends IBaseComp> T createAndInject(Class<T> iface, SubComponentNames sub) {
        Init meta = IClassLoader.resolvedInit(iface);

        ModuleClassLoader loader = ModuleClassLoader.getInstance();
        T obj = (sub == SubComponentNames.NONE)
            ? loader.tryCreate(meta.component())
            : loader.tryCreate(meta.component(), sub);
        if (obj == null)
            throw new IllegalStateException("Cannot instantiate " + iface.getSimpleName());

        // wire @Inject fields
        for (Field f : ClassUtils.getAnnotatedFields(obj.getClass(), Inject.class)) {
            f.setAccessible(true);
            Inject inj = f.getAnnotation(Inject.class);
            if (inj.loadAll()) {
                injectAllImplementations(f, obj, inj);   // <-- honors subTypeOf filter
            } else {
                injectSingle(f, obj, inj);               // <-- enforces rule: subTypeOf requires loadAll
            }
            f.setAccessible(false);
        }

        runPostInject(obj);
        return obj;
    }

    private static void injectAllImplementations(Field f, Object host, Inject inj) {
        if (!List.class.isAssignableFrom(f.getType()))
            throw new IllegalStateException("@Inject(loadAll=true) field must be a List : " + f);

        Class<?> elemType = extractGenericType(f);
        Init elemMeta = elemType.getAnnotation(Init.class);
        if (elemMeta == null || !elemMeta.allowMultiple())
            throw new IllegalStateException("Component does not allow multiple: " + elemType);

        Class<?> subIface = inj.subTypeOf();
        List<?> all;

        if (subIface != null && subIface != IBaseComp.class) {
            if (!subIface.isInterface())
                throw new IllegalStateException("@Inject(subTypeOf=...) must be an interface: " + subIface.getName());
            // Filter: only implementations that implement/extend subIface
            all = ModuleClassLoader.getInstance().tryCreateAll(elemMeta.component(), subIface);
        } else {
            // Original behavior: all sub-components
            all = ModuleClassLoader.getInstance().tryCreateAll(elemMeta.component());
        }

        try { f.set(host, Collections.unmodifiableList(all)); }
        catch (IllegalAccessException e) { throw new RuntimeException(e); }

        LOG.debug("Injected {} implementations into {}", all.size(), f);
    }

    @SuppressWarnings("unchecked")
    private static void injectSingle(Field f, Object host, Inject inj) {
        // Enforce: subTypeOf requires loadAll=true
        if (inj.subTypeOf() != IBaseComp.class) {
            throw new IllegalStateException("@Inject(subTypeOf=...) requires loadAll=true on field: " + f);
        }

        Class<IBaseComp> depType = (Class<IBaseComp>) f.getType();
        SubComponentNames sub = inj.subComp();
        Supplier<IBaseComp> create = () -> {
            if (inj.createNew()) {
                if (sub != SubComponentNames.NONE)
                    return ModuleClassLoader.getInstance().tryCreate(
                        depType.getAnnotation(Init.class).component(), sub);
                return ModuleClassLoader.getInstance().tryCreate(
                    depType.getAnnotation(Init.class).component());
            }
            return sub == SubComponentNames.NONE
                ? getInstance(depType)
                : getInstance(depType, sub);
        };
        Object proxy = LazyProxy.of(depType, create, inj.immortal());
        try { f.set(host, proxy); }
        catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }

    private static Class<?> extractGenericType(Field listField) {
        Type g = listField.getGenericType();
        if (g instanceof ParameterizedType pt) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof Class<?> c) return c;
        }
        throw new IllegalStateException("Cannot resolve List element type for " + listField);
    }

    private static String cacheKey(Class<?> t, SubComponentNames sub) {
        return t.getName() + '#' + sub.name();
    }

    public static Collection<IBaseComp> allCachedInstances() {
        return List.copyOf(INSTANCES.values());
    }

    private static void runPostInject(Object host) {
        Deque<Method> chain = new ArrayDeque<>();
        Class<?> c = host.getClass();
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(PostInject.class)) {
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new IllegalStateException("@PostInject must not be static: " + m);
                    if (m.getParameterCount() != 0)
                        throw new IllegalStateException("@PostInject must have no params: " + m);
                    if (m.getReturnType() != void.class)
                        throw new IllegalStateException("@PostInject must return void: " + m);
                    chain.addFirst(m);
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : chain) {
            try { m.setAccessible(true); m.invoke(host); }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Error invoking @PostInject: " + m, e);
            }
        }
    }
}
