package com.gwngames.core.util;

import com.gwngames.core.api.cfg.IParam;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Holds all ParamKeys created in the JVM. */
public final class ParamRegistry {
    private static final Set<IParam<?>> ALL = ConcurrentHashMap.newKeySet();

    public static void register(IParam<?> key) { ALL.add(key); }

    public static List<IParam<?>> all() {
        return List.copyOf(ALL);
    }
    public static List<IParam<?>> userParams() {
        return ALL.stream().filter(IParam::userModifiable).collect(Collectors.toList());
    }

    /** Ensures static fields are loaded so their ParamKeys get registered. */
    public static void forceLoad(Class<?>... holders) {
        for (Class<?> c : holders) {
            try { Class.forName(c.getName(), true, c.getClassLoader()); }
            catch (ClassNotFoundException ignored) { }
        }
    }

    private ParamRegistry() {}
}
