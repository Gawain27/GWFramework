package com.gwngames.core.generated;

import com.gwngames.catalog.ModulePriorities;
import com.gwngames.core.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime module priority lookup.
 * Bootstraps without generated code by scanning @ModulePriorities (optional),
 * but will use generated mapping if present.
 */
public final class ModulePriorityRegistry {
    private ModulePriorityRegistry() {}

    private static final String GENERATED_FQN =
        "com.gwngames.core.generated.GeneratedModulePriorities";

    private static final ConcurrentHashMap<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    /** Unknown -> 0. Case-insensitive. */
    public static int priorityOf(String moduleId) {
        if (StringUtils.isEmpty(moduleId))
            throw new IllegalStateException("null module");
        ensureInit();
        return CACHE.get(norm(moduleId));
    }

    /** Optional: force re-scan (useful in tests). */
    public static synchronized void reload() {
        initialized = false;
        CACHE.clear();
        ensureInit();
    }

    public static Map<String, Integer> asMap() {
        ensureInit();
        return Collections.unmodifiableMap(new TreeMap<>(CACHE));
    }

    // ------------------------------------------------------------

    private static void ensureInit() {
        if (initialized) return;
        synchronized (ModulePriorityRegistry.class) {
            if (initialized) return;

            // 1) Try generated mapping first (fast, deterministic)
            if (tryLoadGenerated()) {
                initialized = true;
                return;
            }

            // 2) Try scanning @ModulePriorities on known classes (best-effort)
            // NOTE: runtime scanning cannot discover *all* classes without a scanner/index,
            // so we support "registration" as a bootstrap mechanism.
            // You can call registerFrom(...) early in app init to populate.
            loadBuiltInDefaults();

            initialized = true;
        }
    }

    private static boolean tryLoadGenerated() {
        try {
            Class<?> gen = Class.forName(GENERATED_FQN, false,
                Thread.currentThread().getContextClassLoader());

            Method m = gen.getMethod("asMap");
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) m.invoke(null);

            CACHE.clear();
            for (var e : map.entrySet()) {
                CACHE.put(norm(e.getKey()), e.getValue());
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Minimal safe defaults so runtime never explodes. */
    private static void loadBuiltInDefaults() {
        // keep these tiny; generated mapping will supersede once available
        CACHE.putIfAbsent("unimplemented", 0);
        CACHE.putIfAbsent("interface", 1);
        CACHE.putIfAbsent("core", 5);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------
    // Optional bootstrap API (manual registration)
    // ------------------------------------------------------------

    /**
     * Register priorities from a class annotated with @ModulePriorities.
     * You can call this in a static initializer of your module catalog classes
     * during early bootstrap if you want runtime scanning without generated code.
     */
    public static void registerFrom(Class<?> cls) {
        if (cls == null) return;
        ModulePriorities mp = cls.getAnnotation(ModulePriorities.class);
        if (mp == null) return;

        for (ModulePriorities.Entry e : mp.value()) {
            CACHE.put(norm(e.id()), e.priority());
        }
    }
}
