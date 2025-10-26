package com.gwngames.core.base;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base component class used to extend components of the next module<br>
 * Concrete implementation of an overridden interface automatically
 * wires all the java inheritance mechanisms.<br>
 * You basically override components without knowing the effective component!
 * @see com.gwngames.core.util.ClosestWeaver
 * @see com.gwngames.core.util.ClosestScan
 * @see com.gwngames.core.util.TransformingURLClassLoader
 * */
public abstract class ClosestComponent extends BaseComponent {

    /**
     * Marker: "call the super implementation of the CURRENT method using the CURRENT arguments".
     * This must be rewritten by ClosestWeaver; calling at runtime without weaving throws.
     */
    protected final <T> T callSuper() {
        throw new IllegalStateException("ClosestComponent.callSuper() must be rewritten by ClosestWeaver");
    }

    /**
     * Marker: "call the super implementation of the CURRENT method using the provided arguments".
     * The args array length must equal the arity of the overriding method. Elements must be
     * assignable/boxable to the parameter types. Rewritten by ClosestWeaver.
     */
    protected final <T> T callSuperReplaceArgs(Object... args) {
        throw new IllegalStateException("ClosestComponent.callSuperReplaceArgs(...) must be rewritten by ClosestWeaver");
    }

    /* ───────────────────────── super-field helpers ───────────────────────── */

    /**
     * Read a field declared in the superclass chain (starting from the immediate super).
     * This unchecked variant simply casts the value to T.
     */
    @SuppressWarnings("unchecked")
    protected final <T> T superField(String name) {
        Object v = readSuperFieldValue(name);
        return (T) v;
    }

    /**
     * Read a field declared in the superclass chain (starting from the immediate super),
     * with a checked cast to the requested type. Primitive {@code type} is supported.
     */
    protected final <T> T superField(String name, Class<T> type) {
        Object v = readSuperFieldValue(name);
        if (v == null) return null; // caller must accept null when field is reference type
        Class<?> target = wrapPrimitive(type);
        if (!target.isInstance(v)) {
            throw new ClassCastException("Field '" + name + "' is " +
                v.getClass().getName() + ", not " + target.getName());
        }
        @SuppressWarnings("unchecked")
        T cast = (T) v;
        return cast;
    }

    private Object readSuperFieldValue(String name) {
        Class<?> cur = getClass().getSuperclass(); // skip this class; start at immediate super
        while (cur != null && cur != Object.class) {
            Field f = resolveField(cur, name);
            if (f != null) {
                try {
                    return f.get(this); // instance field
                } catch (IllegalAccessException e) {
                    // Shouldn't happen after setAccessible(true); wrap to be explicit
                    throw new IllegalStateException("Cannot access field '" + name +
                        "' on " + cur.getName(), e);
                }
            }
            cur = cur.getSuperclass();
        }
        throw new IllegalArgumentException("No such field in super chain: '" + name + "'");
    }

    /* ───────────────────────── caching + utils ───────────────────────── */

    private static final ConcurrentHashMap<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private static Field resolveField(Class<?> owner, String name) {
        Map<String, Field> byName = FIELD_CACHE.computeIfAbsent(owner, k -> new ConcurrentHashMap<>());
        Field cached = byName.get(name);
        if (cached != null) return cached;

        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            byName.put(name, f);
            return f;
        } catch (NoSuchFieldException e) {
            byName.put(name, null); // remember miss for this owner
            return null;
        }
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type; // void.class not expected here
    }
}

