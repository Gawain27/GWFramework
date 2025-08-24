package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;
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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 *
 * Root-class for every non-enum GW-Framework component.
 *
 * <p><b>What’s new</b></p>
 * <ul>
 *   <li>Each concrete instance receives a <strong>monotonically
 *       increasing integer</strong> (<code>multId</code>) at construction
 *       time.  This is completely independent of
 *       {@link SubComponentNames}.</li>
 *   <li>Enum components are not affected because an <code>enum</code>
 *       cannot extend a regular class anyway – they typically implement
 *       {@link IBaseComp} directly and may expose their own identifier
 *       (e.g.&nbsp;<code>ordinal()</code>).</li>
 *   <li>Thread-safe singleton cache (<em>per sub-component</em>).</li>
 *   <li>Fields annotated with {@link Inject} are populated lazily:
 *       the underlying object is materialised on first method call.</li>
 *   <li>{@link Inject#subComp()} lets you choose a concrete sub-component
 *       implementation; the same key is used for the singleton cache,
 *       so identical requests share the instance.</li>
 * </ul>
 */
public abstract class BaseComponent implements IBaseComp, IDashboardItem {

    /** Identifier used reflectively (assigned once on construction). */
    private final int multId = ComponentUtils.assign(this);

    public enum DashTable { COMPONENTS }
    public enum DashCategory { ALL }
    public enum DashICat { DEFAULT }

    // --- IDashboardItem placement (defaults) ---
    @Override public Enum<?> tableKey()        { return DashTable.COMPONENTS; }
    @Override public Enum<?> categoryKey()     { return DashCategory.ALL; }
    @Override public Enum<?> itemCategoryKey() { return DashICat.DEFAULT; }
    // We render this item directly in CoreDashboard (no external content)
    @Override public String templateId()       { return "component-item"; }
    @Override public SubComponentNames contentSubComp() { return SubComponentNames.NONE; }

    // --- OPTIONAL: convenient logging wrappers that also feed LogBus ---
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

    // --- dashboard identity helpers ---
    public final String dashboardKey() {
        return getClass().getName() + "#" + multId;
    }
    public String dashboardTitle() {
        return getClass().getSimpleName() + " #" + multId;
    }
    @Override
    public void setMultId(int newId) { /* regular comps ignore external set */ }

    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    /** One cached instance for each Component + SubComp pair. */
    private static final Map<String, IBaseComp> INSTANCES = new ConcurrentHashMap<>();

    /* ===================== Public lookup helpers ===================== */

    /** Default: cached singleton (SubComponentNames.NONE). */
    public static <T extends IBaseComp> T getInstance(Class<T> type) {
        return getInstance(type, SubComponentNames.NONE, false);
    }

    /** Default: cached singleton for given sub-component. */
    public static <T extends IBaseComp> T getInstance(Class<T> type, SubComponentNames sub) {
        return getInstance(type, sub, false);
    }

    /**
     * Optionally get a fresh (non-cached) instance. Equivalent to
     * {@code getInstance(type, SubComponentNames.NONE, fresh)}.
     */
    public static <T extends IBaseComp> T getInstance(Class<T> type, boolean fresh) {
        return getInstance(type, SubComponentNames.NONE, fresh);
    }

    /**
     * Main entry: choose between cached singleton (fresh=false) or a brand-new
     * instance (fresh=true). Fresh instances are NOT stored in the cache.
     *
     * @param type  interface annotated with @Init
     * @param sub   sub-component (or NONE)
     * @param fresh if true, always constructs a new instance and skips the cache
     */
    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> type,
                                                      SubComponentNames sub,
                                                      boolean fresh) {
        if (!fresh) {
            return (T) INSTANCES.computeIfAbsent(cacheKey(type, sub),
                k -> createAndInject(type, sub));
        }
        // fresh instance – do not cache
        return createAndInject(type, sub);
    }

    /* ================= instantiation & @Inject wiring ================= */

    private static <T extends IBaseComp> T createAndInject(Class<T> iface, SubComponentNames sub) {
        Init meta = ModuleClassLoader.resolvedInit(iface);

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
                injectAllImplementations(f, obj);
            } else {
                injectSingle(f, obj, inj);
            }
            f.setAccessible(false);
        }

        runPostInject(obj);
        return obj;
    }

    private static void injectAllImplementations(Field f, Object host) {
        if (!List.class.isAssignableFrom(f.getType()))
            throw new IllegalStateException("@Inject(loadAll=true) field must be a List : " + f);

        Class<?> elemType = extractGenericType(f);
        Init elemMeta = elemType.getAnnotation(Init.class);
        if (elemMeta == null || !elemMeta.allowMultiple())
            throw new IllegalStateException("Component does not allow multiple: " + elemType);

        List<?> all = ModuleClassLoader.getInstance().tryCreateAll(elemMeta.component());
        try { f.set(host, Collections.unmodifiableList(all)); }
        catch (IllegalAccessException e) { throw new RuntimeException(e); }

        LOG.debug("Injected {} implementations into {}", all.size(), f);
    }

    @SuppressWarnings("unchecked")
    private static void injectSingle(Field f, Object host, Inject inj) {
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
                ? getInstance(depType)                      // cached
                : getInstance(depType, sub);               // cached
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

    private static void runPostInject(Object host) {
        // Collect methods in superclass -> subclass order
        Deque<Method> chain = new ArrayDeque<>();
        Class<?> c = host.getClass();
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(PostInject.class)) {
                    // Validate signature: non-static, void, no params
                    if (Modifier.isStatic(m.getModifiers()))
                        throw new IllegalStateException("@PostInject method must not be static: " + m);
                    if (m.getParameterCount() != 0)
                        throw new IllegalStateException("@PostInject method must have no parameters: " + m);
                    if (m.getReturnType() != void.class)
                        throw new IllegalStateException("@PostInject method must return void: " + m);
                    chain.addFirst(m); // run superclass hooks first
                }
            }
            c = c.getSuperclass();
        }

        // Invoke in order
        for (Method m : chain) {
            try {
                m.setAccessible(true);
                m.invoke(host);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Error invoking @PostInject: " + m, e);
            }
        }
    }

}

