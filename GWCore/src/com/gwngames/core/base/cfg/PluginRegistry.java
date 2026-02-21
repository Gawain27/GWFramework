package com.gwngames.core.base.cfg;

import com.gwngames.core.api.build.IPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads plugins via ServiceLoader and selects the highest-priority implementation.
 * <p>
 * IMPORTANT: Providers may live in dynamically loaded module JARs, so we prefer
 * {@link ModuleClassLoader} (the module runtime loader) when present.
 * Higher modules override lower modules by {@link IPlugin#priority()}.
 */
public final class PluginRegistry {

    private static final Map<Class<? extends IPlugin>, IPlugin> SELECTED = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IPlugin>, List<IPlugin>> ALL = new ConcurrentHashMap<>();

    private PluginRegistry() {}

    public static synchronized <T extends IPlugin> T get(Class<T> type) {
        IPlugin cached = SELECTED.get(type);
        if (cached != null) return type.cast(cached);

        ClassLoader cl = resolvePluginClassLoader();

        ServiceLoader<T> loader = ServiceLoader.load(type, cl);

        List<T> found = new ArrayList<>();
        loader.forEach(found::add);

        // fallback: try the default loader as well (useful in tests/IDE)
        if (found.isEmpty()) {
            ServiceLoader<T> fallback = ServiceLoader.load(type);
            fallback.forEach(found::add);
        }

        found.sort(Comparator.comparingInt(IPlugin::priority).reversed());
        ALL.put(type, List.copyOf(found));

        if (found.isEmpty()) return null;

        T best = found.getFirst();
        SELECTED.put(type, best);
        return best;
    }

    /** For diagnostics/tests. Sorted DESC by priority. */
    @SuppressWarnings("unchecked")
    public static <T extends IPlugin> List<T> list(Class<T> type) {
        return (List<T>) (List<?>) ALL.getOrDefault(type, List.of());
    }

    /** Hard override for tests or bootstrapping. */
    public static synchronized <T extends IPlugin> void override(Class<T> type, T instance) {
        SELECTED.put(type, instance);
    }

    public static synchronized void reset() {
        SELECTED.clear();
        ALL.clear();
    }

    private static ClassLoader resolvePluginClassLoader() {
        // 1) Prefer module loader so ServiceLoader can see providers in module JARs
        try {
            // If ModuleClassLoader is not initialized yet, this call will initialize it.
            // If you do NOT want plugin lookup to force init, see the note below.
            return ModuleClassLoader.getInstance();
        } catch (Throwable ignored) {
            // 2) Fall back to context loader (tests / IDE)
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) return tccl;

            // 3) Last resort
            return PluginRegistry.class.getClassLoader();
        }
    }
}
