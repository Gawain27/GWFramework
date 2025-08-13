package com.gwngames.core.util;

import com.gwngames.core.api.base.IBaseComp;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Manages miscellaneous operations regarding component infrastructure<br>
 * Hands out unique mult-ids across the whole JVM for enums + objects. */
public final class ComponentUtils {
    private static final AtomicInteger seq = new AtomicInteger(0);

    // enums never die, singletons almost never die; but for safety keep WeakReference
    private static final Map<Integer, WeakReference<IBaseComp>> byId = new ConcurrentHashMap<>();


    /** Next unique id. */
    public static int next() { return seq.getAndIncrement(); }

    /** Assign and register id for a normal object (BaseComponent subclass). */
    public static int register(IBaseComp obj, int id) {
        byId.put(id, new WeakReference<>(obj));
        return id;
    }

    /** Assign a new id for an object and register it. */
    public static int assign(IBaseComp obj) {
        int id = next();
        byId.put(id, new WeakReference<>(obj));
        return id;
    }

    /** Assign & push to the enum through setMultId. */
    public static int assignEnum(IBaseComp enumConst) {
        int id = next();
        enumConst.setMultId(id);           // enum branch in IBaseComp will accept this
        byId.put(id, new WeakReference<>(enumConst));
        return id;
    }

    /** Reverse lookup: id → live instance (if still reachable). */
    public static Optional<IBaseComp> lookup(int id) {
        WeakReference<IBaseComp> ref = byId.get(id);
        return Optional.ofNullable(ref == null ? null : ref.get());
    }
}
