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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared functionality for every GW-Framework component.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe singleton cache (<em>per sub-component</em>).</li>
 *   <li>Fields annotated with {@link Inject} are populated lazily:
 *       the underlying object is materialised on first method call.</li>
 *   <li>{@link Inject#subComp()} lets you choose a concrete sub-component
 *       implementation; the same key is used for the singleton cache,
 *       so identical requests share the instance.</li>
 * </ul>
 *
 * @author samlam
 */
public abstract class BaseComponent implements IBaseComp {

    /* ───────────────────────── logging & cache ─────────────────────── */
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    /**
     * One cache entry per <code>Class&nbsp;+&nbsp;SubComponent</code> pair.
     * Key format: <code>fqcn#SUBNAME</code> (&quot;NONE&quot; for no sub-comp).
     */
    private static final Map<String, IBaseComp> INSTANCES = new ConcurrentHashMap<>();

    /* ───────────────────────── public lookup API ────────────────────── */

    /** Singleton for the “default” implementation (subComp = NONE). */
    public static <T extends IBaseComp> T getInstance(Class<T> type) {
        return getInstance(type, SubComponentNames.NONE);
    }

    /** Singleton for a specific sub-component implementation. */
    @SuppressWarnings("unchecked")
    public static <T extends IBaseComp> T getInstance(Class<T> type,
                                                      SubComponentNames sub) {
        String key = cacheKey(type, sub);
        LOG.debug("Looking up: {}", key);
        return (T) INSTANCES.computeIfAbsent(key,
            k -> createAndInject(type, sub));
    }

    /* ───────────────────────── object creation ─────────────────────── */

    /**
     * Finds a concrete implementation (respecting {@code sub}),
     * instantiates it, and lazily injects its dependencies.
     */
    @SuppressWarnings("unchecked")
    private static <T extends IBaseComp> T createAndInject(Class<T> iface,
                                                           SubComponentNames sub) {

        Init init = iface.getAnnotation(Init.class);
        if (init == null)
            throw new IllegalStateException("Missing @Init on " + iface.getSimpleName());

        ModuleClassLoader loader = ModuleClassLoader.getInstance();
        T instance = (sub == SubComponentNames.NONE)
            ? loader.tryCreate(init.component())
            : loader.tryCreate(init.component(), sub);

        if (instance == null)
            throw new IllegalStateException("No component for " + iface.getSimpleName());

        /* ── handle @Inject fields lazily ── */
        List<Field> fields = ClassUtils.getAnnotatedFields(instance.getClass(), Inject.class);

        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Inject inj = f.getAnnotation(Inject.class);
                Class<T> depType = (Class<T>) f.getType();
                SubComponentNames targetSub = inj.subComp();

                Supplier<T> supplier = () -> {              // executed on first use
                    if (inj.createNew()) {
                        /* brand-new instance, honour subComp if supplied */
                        if (targetSub != SubComponentNames.NONE) {
                            return loader.tryCreate(depType
                                    .getAnnotation(Init.class).component(),
                                targetSub, depType);
                        }
                        return loader.tryCreate(depType
                            .getAnnotation(Init.class).component(), depType);
                    }
                    /* singleton path */
                    if (targetSub != SubComponentNames.NONE)
                        return getInstance(depType, targetSub);
                    return getInstance(depType);
                };

                Object proxyOrObj = LazyProxy.of(depType, supplier, inj.immortal());
                f.set(instance, proxyOrObj);

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Injection failed for "
                    + instance.getClass().getSimpleName() + "." + f.getName(), e);
            } finally {
                f.setAccessible(false);
            }
        }
        return instance;
    }

    /* ───────────────────────── helpers ─────────────────────────────── */

    private static String cacheKey(Class<?> type, SubComponentNames sub) {
        return type.getName() + '#' + sub.name();
    }
}
