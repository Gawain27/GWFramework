package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.util.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
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
public abstract class BaseComponent implements IBaseComp {

    /* ────────────────────────── instance id ─────────────────────────── */

    /** Thread-safe global counter – starts at 1 for nicer logs. */
    private static final AtomicInteger ID_SEQ = new AtomicInteger(0);

    /** Identifier that the ModuleClassLoader logs when the instance is created. */
    private final int multId = ID_SEQ.incrementAndGet();

    @Override public int getMultId() { return multId; }

    /* ────────────────────────── singleton cache ─────────────────────── */

    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    /**
     * One cached instance for each <code>Component&nbsp;+&nbsp;SubComp</code>
     * pair.  Key example: <code>"gw.SomeComponent#AUDIO"</code>
     */
    private static final Map<String, IBaseComp> INSTANCES = new ConcurrentHashMap<>();

    /* ===== public lookup helpers ===================================== */

    public static <T extends IBaseComp> T getInstance(Class<T> type) {
        return getInstance(type, SubComponentNames.NONE);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> type,
                                                      SubComponentNames sub) {
        return (T) INSTANCES.computeIfAbsent(cacheKey(type, sub),
            k -> createAndInject(type, sub));
    }

    /* ───────────────────── instantiation & @Inject ──────────────────── */

    private static <T extends IBaseComp> T createAndInject(Class<T> iface, SubComponentNames sub) {
        Init meta = iface.getAnnotation(Init.class);
        if (meta == null)
            throw new IllegalStateException("Missing @Init on " + iface.getSimpleName());

        ModuleClassLoader loader = ModuleClassLoader.getInstance();
        T obj = (sub == SubComponentNames.NONE)
            ? loader.tryCreate(meta.component())
            : loader.tryCreate(meta.component(), sub);

        if (obj == null)
            throw new IllegalStateException("Cannot instantiate " + iface.getSimpleName());

        /* ── wire @Inject fields (supports loadAll/createNew/immortal) ── */
        for (Field f : ClassUtils.getAnnotatedFields(obj.getClass(), Inject.class)) {
            f.setAccessible(true);
            Inject inj = f.getAnnotation(Inject.class);

            if (inj.loadAll()) {                              // List<T> injection
                injectAllImplementations(f, obj);
            } else {                                          // classic singleton / new
                injectSingle(f, obj, inj);
            }
            f.setAccessible(false);
        }
        return obj;
    }

    /* ===== helpers for @Inject processing ============================ */
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
}
