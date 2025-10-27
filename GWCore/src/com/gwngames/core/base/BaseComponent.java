package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.base.monitor.IDashboardItem;
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
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Root-class for every non-enum GW component.
 */
public abstract class BaseComponent implements IBaseComp, IDashboardItem<BaseComponent> {

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
                injectAllImplementations(f, obj, inj);   // <-- now returns LazyProxy list
            } else {
                injectSingle(f, obj, inj);               // unchanged
            }
            f.setAccessible(false);
        }

        runPostInject(obj);
        return obj;
    }

    /**
     * Creates a List of LazyProxy elements for all allowed implementations.
     * Honors {@code subTypeOf} filter like before, but does NOT eagerly expose real instances.
     * Notes:
     *  - We still need to discover candidates via the loader. The current loader API returns instances;
     *    we use those only to learn the concrete classes, then build suppliers that create on first use.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void injectAllImplementations(Field f, Object host, Inject inj) {
        if (!List.class.isAssignableFrom(f.getType()))
            throw new IllegalStateException("@Inject(loadAll=true) field must be a List : " + f);

        final Class<?> elemType = ClassUtils.extractGenericType(f);
        if (!IBaseComp.class.isAssignableFrom(elemType))
            throw new IllegalStateException("@Inject(loadAll=true) element type must extend IBaseComp : " + elemType);

        final Init elemMeta = elemType.getAnnotation(Init.class);
        if (elemMeta == null || !elemMeta.allowMultiple())
            throw new IllegalStateException("Component does not allow multiple: " + elemType);

        final Class<?> subIface = inj.subTypeOf();
        final ModuleClassLoader loader = ModuleClassLoader.getInstance();

        // Discover implementations (existing API yields instances).
        final List<?> discovered = (subIface != null && subIface != IBaseComp.class)
            ? loader.tryCreateAll(elemMeta.component(), subIface)
            : loader.tryCreateAll(elemMeta.component());

        // Build a proxy per element that creates its concrete impl lazily on first call.
        final boolean immortal = inj.immortal();
        final List proxies = new ArrayList(discovered.size());

        for (Object impl : discovered) {
            final Supplier<IBaseComp> supplier = getIBaseCompSupplier((IBaseComp) impl, loader);

            Object proxy = LazyProxy.of((Class) elemType, supplier, immortal);
            proxies.add(proxy);
        }

        try {
            f.set(host, Collections.unmodifiableList(proxies));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        LOG.debug("Injected {} LazyProxy implementations into {}", proxies.size(), f);
    }

    @NotNull
    private static Supplier<IBaseComp> getIBaseCompSupplier(IBaseComp impl, ModuleClassLoader loader) {
        final Class<? extends IBaseComp> implClass = impl.getClass();

        // Supplier that creates a fresh instance of the concrete class on first use.
        return new Supplier<>() {
            private volatile IBaseComp cached;
            @Override public IBaseComp get() {
                IBaseComp t = cached;
                if (t == null) {
                    synchronized (this) {
                        t = cached;
                        if (t == null) {
                            t = (IBaseComp) loader.createInstance(implClass);
                            cached = t;
                        }
                    }
                }
                return t;
            }
            @Override public String toString() { return "supplier(" + implClass.getSimpleName() + ")"; }
        };
    }

    @SuppressWarnings("unchecked")
    private static void injectSingle(Field f, Object host, Inject inj) {
        // Enforce: subTypeOf requires loadAll=true
        if (inj.subTypeOf() != IBaseComp.class) {
            throw new IllegalStateException("@Inject(subTypeOf=...) requires loadAll=true on field: " + f);
        }

        Class<IBaseComp> depType = (Class<IBaseComp>) f.getType();
        Supplier<IBaseComp> create = getIBaseCompSupplier(inj, depType);
        Object proxy = LazyProxy.of(depType, create, inj.immortal());
        try { f.set(host, proxy); }
        catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }

    @NotNull
    private static Supplier<IBaseComp> getIBaseCompSupplier(Inject inj, Class<IBaseComp> depType) {
        SubComponentNames sub = inj.subComp();
        return () -> {
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

    @Override
    public BaseComponent getItem(){
        return this;
    }
}
