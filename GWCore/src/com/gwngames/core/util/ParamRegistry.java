package com.gwngames.core.util;

import com.gwngames.core.api.build.ParamClass;
import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Holds all ParamKeys created in the JVM. */
public final class ParamRegistry {
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final Set<Class<?>> INITIALIZED = new HashSet<>();
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

    public static synchronized void loadAll() {
        var mcl = ModuleClassLoader.getInstance();
        List<Class<?>> holders = mcl.scanForAnnotated(ParamClass.class);

        int initialized = 0;
        for (Class<?> c : holders) {
            if (INITIALIZED.add(c)) {
                forceInitialize(c);
                initialized++;
            }
        }
        log.info("ParamKeyDiscovery: initialized {} @ParamClass holders (total seen: {})",
            initialized, INITIALIZED.size());
    }

    private static void forceInitialize(Class<?> c) {
        try {
            // true => run static initializers; use the class’ own loader
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to initialize @ParamClass: " + c.getName(), e);
        }
    }

    private ParamRegistry() {}
}
