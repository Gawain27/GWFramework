package com.gwngames.core.base;

import com.gwngames.core.CoreSubComponent;
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

    /**
     * Per-instance id (assigned once).
     */
    private final int multId = ComponentUtils.assign(this);

    /**
     * System logger (subclasses may also use their own FileLogger).
     */
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    /**
     * One cached instance for each Component + SubComp pair.
     */
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

    @Override
    public void setMultId(int newId) { /* regular comps ignore external set */ }

    /* ===================== Public lookup helpers ===================== */

    public static <T extends IBaseComp> T getInstance(Class<T> type) {
        return getInstance(type, CoreSubComponent.NONE, false);
    }

    public static <T extends IBaseComp> T getInstance(Class<T> type, String sub) {
        return getInstance(type, sub, false);
    }

    public static <T extends IBaseComp> T getInstance(Class<T> type, boolean fresh) {
        return getInstance(type, CoreSubComponent.NONE, fresh);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> type,
                                                      String sub,
                                                      boolean fresh) {
        if (!fresh) {
            return (T) INSTANCES.computeIfAbsent(cacheKey(type, sub),
                k -> createAndInject(type, sub));
        }
        return createAndInject(type, sub); // fresh, not cached
    }

    /* ================= instantiation & @Inject wiring ================= */

    private static <T extends IBaseComp> T createAndInject(Class<T> iface, String sub) {
        Init meta = IClassLoader.resolvedInit(iface);

        ModuleClassLoader loader = ModuleClassLoader.getInstance();
        T obj = (sub.equals(CoreSubComponent.NONE))
            ? loader.tryCreate(meta.component())
            : loader.tryCreate(meta.component(), sub);
        if (obj == null)
            throw new IllegalStateException("Cannot instantiate " + iface.getSimpleName());

        // wire @Inject fields
        wireComponents(obj);
        return obj;
    }

    /** weak-identity set to remember objects that have been wired already */
    private static final Set<Object> WIRED =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static boolean markWired(Object o) {
        synchronized (WIRED) {
            if (WIRED.contains(o))
                return false;
            WIRED.add(o);
            return true;
        }
    }

    private static <T extends IBaseComp> void wireComponents(T obj) {
        // idempotency guard: if already wired, bail out
        if (!markWired(obj)) return;

        for (Field f : ClassUtils.getAnnotatedFields(obj.getClass(), Inject.class)) {
            f.setAccessible(true);
            Inject inj = f.getAnnotation(Inject.class);
            if (inj.loadAll()) {
                injectAllImplementations(f, obj, inj);
            } else {
                injectSingle(f, obj, inj);
            }
            f.setAccessible(false);
        }
        runPostInject(obj);
    }

    /**
     * Creates a List of LazyProxy elements for all allowed implementations.
     * Honors {@code subTypeOf} filter like before, but does NOT eagerly expose real instances.
     * Notes:
     * - We still need to discover candidates via the loader. The current loader API returns instances;
     * we use those only to learn the concrete classes, then build suppliers that create on first use.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectAllImplementations(Field f, Object host, Inject inj) {
        if (!List.class.isAssignableFrom(f.getType()))
            throw new IllegalStateException("@Inject(loadAll=true) field must be a List : " + f);

        final Class<?> elemType = ClassUtils.extractGenericType(f);
        if (!IBaseComp.class.isAssignableFrom(elemType))
            throw new IllegalStateException("@Inject(loadAll=true) element type must extend IBaseComp : " + elemType);

        final Init elemMeta = IClassLoader.resolvedInit(elemType);
        if (!elemMeta.allowMultiple())
            throw new IllegalStateException("Component does not allow multiple: " + elemType);

        final Class<?> subIface = inj.subTypeOf();
        final ModuleClassLoader loader = ModuleClassLoader.getInstance();

        // Discover implementations (existing API yields instances).
        // In BaseComponent.injectAllImplementations(...)
        final List<Class<?>> classes =
            (subIface != null && subIface != IBaseComp.class)
                ? loader.listSubComponents(elemMeta.component(), subIface)
                : loader.listSubComponents(elemMeta.component());

        final List proxies = new ArrayList(classes.size());

        for (Class<?> implClass : classes) {
            Supplier<IBaseComp> supplier = getIBaseCompSupplier((Class<? extends IBaseComp>) implClass, loader);

            Object proxy = LazyProxy.of((Class) elemType, supplier);
            proxies.add(proxy);
        }

        try {
            f.set(host, proxies);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Can't inject implementations:" + f.getName(), e);
        }
        LOG.debug("Injected {} LazyProxy implementations into {}", proxies.size(), f);
    }

    @NotNull
    private static Supplier<IBaseComp> getIBaseCompSupplier(Class<? extends IBaseComp> implClass, ModuleClassLoader loader) {
        return new Supplier<>() {
            private volatile IBaseComp cached;

            @Override
            public IBaseComp get() {
                IBaseComp t = cached;
                if (t == null) {
                    synchronized (this) {
                        t = cached;
                        if (t == null) {
                            // create the concrete instance
                            IBaseComp fresh = (IBaseComp) loader.createInstance(implClass);

                            // wire @Inject fields on the fresh instance (same as createAndInject)
                            wireComponents(fresh);

                            t = cached = fresh;
                        }
                    }
                }
                return t;
            }

            @Override
            public String toString() {
                return "supplier(" + implClass.getSimpleName() + ")";
            }
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
        Object proxy = LazyProxy.of(depType, create);
        try {
            f.set(host, proxy);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static Supplier<IBaseComp> getIBaseCompSupplier(Inject inj, Class<IBaseComp> depType) {
        String sub = inj.subComp();
        return () -> {
            if (inj.createNew()) {
                if (!sub.equals(CoreSubComponent.NONE))
                    return ModuleClassLoader.getInstance().tryCreate(
                        depType.getAnnotation(Init.class).component(), sub);
                return ModuleClassLoader.getInstance().tryCreate(
                    depType.getAnnotation(Init.class).component());
            }
            return sub.equals(CoreSubComponent.NONE)
                ? getInstance(depType)
                : getInstance(depType, sub);
        };
    }


    private static String cacheKey(Class<?> t, String sub) {
        return t.getName() + '#' + sub;
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
            try {
                m.setAccessible(true);
                m.invoke(host);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Error invoking @PostInject: " + m, e);
            }
        }
    }

    @Override
    public BaseComponent getItem() {
        return this;
    }

    @Override
    public String dashboardKey() {
        Init meta = IClassLoader.resolvedInit(this.getClass());
        if (meta.isEnum())
            this.ensureEnum();
        return this.toString();
    }
}
