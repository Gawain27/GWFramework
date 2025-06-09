package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lazy proxy with idle-time eviction.
 */
public final class LazyProxy {

    /* ───────────────────────── configuration ───────────────────────── */
    /**
     * Idle time (ms) after which non-immortal components are evicted.
     * Mutable so tests (or other code) can tweak it at runtime.
     */
    private static volatile long TTL_MS =
        Long.getLong("gw.lazy.ttl", 5 * 60_000);   // 5 minutes default

    /** Expose for tests or other runtime tuning if needed. */
    public static void setTtl(long ttlMillis) {
        TTL_MS = ttlMillis;
    }
    /* ───────────────────────── factory helpers ─────────────────────── */
    public static <T extends IBaseComp> T of(Class<T> iface,
                                             Supplier<T> supplier,
                                             boolean immortal) {
        if (!iface.isInterface()) return supplier.get();

        @SuppressWarnings("unchecked")                     // safe: we control the interfaces array
        T proxy = (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{iface},
            new Handler<>(supplier, immortal));
        return proxy;
    }

    public static <T extends IBaseComp> T of(Class<T> iface,
                                             Supplier<T> supplier) {
        return of(iface, supplier, false);
    }

    /* ───────────────────────── invocation handler ──────────────────── */
    private static final class Handler<T> implements InvocationHandler {

        private final Supplier<T> supplier;
        private final boolean immortal;
        /* ✱ explicit type argument to silence -Xlint:unchecked */
        private volatile WeakReference<T> ref = new WeakReference<>((T) null);
        private volatile long lastUse = System.currentTimeMillis();

        Handler(Supplier<T> s, boolean immortal) {
            this.supplier = s;
            this.immortal = immortal;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {

            switch (m.getName()) {                 // Object methods – never forward
                case "hashCode" -> { return System.identityHashCode(proxy); }
                case "equals"   -> { return proxy == args[0]; }
                case "toString" -> { return "LazyProxy<" + supplier + ">"; }
            }

            long now = System.currentTimeMillis();
            T target = ref.get();

            /* idle-time eviction */
            if (!immortal && target != null && now - lastUse > TTL_MS) {
                ref = new WeakReference<>((T) null);
                target = null;
            }

            /* lazy creation */
            if (target == null) {
                synchronized (this) {
                    target = ref.get();
                    if (target == null) {
                        target = Objects.requireNonNull(
                            supplier.get(), "Lazy supplier returned null");
                        ref = new WeakReference<>(target);
                    }
                }
            }
            lastUse = now;

            /* default-method support (JDK 9+) */
            if (m.isDefault()) {
                Class<?> declaring = m.getDeclaringClass();
                MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());

                MethodType type = MethodType.methodType(
                    m.getReturnType(), m.getParameterTypes());

                MethodHandle handle = lookup.findSpecial(
                    declaring, m.getName(), type, declaring);

                return handle.bindTo(target)
                    .invokeWithArguments(args == null ? new Object[0] : args);
            }

            return m.invoke(target, args);
        }
    }

    private LazyProxy() {}   // utility class
}
