package com.gwngames.core.base.cfg;

import com.gwngames.core.api.build.IPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads plugins via ServiceLoader and selects the highest-priority implementation.
 * Higher modules override lower modules by priority().
 */
public final class PluginRegistry {

    private static final Map<Class<? extends IPlugin>, IPlugin> SELECTED = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IPlugin>, List<IPlugin>> ALL = new ConcurrentHashMap<>();

    private PluginRegistry() {}

    public static synchronized <T extends IPlugin> T get(Class<T> type) {
        IPlugin cached = SELECTED.get(type);
        if (cached != null) return type.cast(cached);

        List<IPlugin> found = new ArrayList<>();
        ServiceLoader.load(type).forEach(found::add);

        found.sort(Comparator.comparingInt(IPlugin::priority).reversed());
        ALL.put(type, List.copyOf(found));

        if (found.isEmpty()) return null;

        IPlugin best = found.getFirst();
        SELECTED.put(type, best);
        return type.cast(best);
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
}
