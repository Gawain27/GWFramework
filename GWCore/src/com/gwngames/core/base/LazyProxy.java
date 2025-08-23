package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lightweight dynamic proxy that:
 *  • lazily creates the real instance on first access
 *  • evicts it after TTL unless immortal == true
 *  • supports default interface methods
 *  • UNWRAPS reflective exceptions so callers see the real cause
 */
public final class LazyProxy {

    /** Idle time in ms before a non-immortal instance is evicted. */
    private static volatile long TTL_MS = Long.getLong("gw.lazy.ttl", 5 * 60_000);
    public static void setTtl(long ttlMillis) { TTL_MS = ttlMillis; }

    public static <T extends IBaseComp> T of(Class<T> iface, Supplier<T> supplier, boolean immortal) {
        if (!iface.isInterface()) return supplier.get();
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{iface},
            new Handler<>(supplier, immortal));
        return proxy;
    }
    public static <T extends IBaseComp> T of(Class<T> iface, Supplier<T> supplier) {
        return of(iface, supplier, false);
    }

    /* ───────────────────────── invocation handler ──────────────────── */
    private static final class Handler<T> implements InvocationHandler {
        private final Supplier<T> supplier;
        private final boolean immortal;

        private volatile WeakReference<T> ref = new WeakReference<>(null);
        private volatile long lastUse = System.currentTimeMillis();

        Handler(Supplier<T> supplier, boolean immortal) {
            this.supplier = supplier;
            this.immortal = immortal;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            // Object methods don't touch target
            switch (m.getName()) {
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == (args != null && args.length > 0 ? args[0] : null);
                case "toString": return "LazyProxy<" + supplier + ">";
            }

            long now = System.currentTimeMillis();
            T target = materialiseTarget(now);

            // Default interface method path (JDK 9+)
            if (m.isDefault()) {
                try {
                    Class<?> declaring = m.getDeclaringClass();
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());
                    MethodHandle handle = lookup.findSpecial(
                        declaring,
                        m.getName(),
                        MethodType.methodType(m.getReturnType(), m.getParameterTypes()),
                        declaring);
                    return handle.bindTo(target).invokeWithArguments(args == null ? new Object[0] : args);
                } catch (Throwable t) {
                    rethrow(t); // unwrap & rethrow as unchecked/error
                    return null; // unreachable
                }
            }

            // Ordinary method
            try {
                if (!m.canAccess(target)) m.setAccessible(true);
                return m.invoke(target, args);
            } catch (InvocationTargetException ite) {
                rethrow(ite.getCause()); // unwrap
                return null; // unreachable
            } catch (IllegalAccessException iae) {
                throw new RuntimeException(iae);
            }
        }

        private T materialiseTarget(long now) {
            T target = ref.get();

            // idle-time eviction
            if (!immortal && target != null && now - lastUse > TTL_MS) {
                ref.clear();
                target = null;
            }

            if (target == null) {
                synchronized (this) {
                    target = ref.get();
                    if (target == null) {
                        target = Objects.requireNonNull(supplier.get(), "Lazy supplier returned null");
                        ref = new WeakReference<>(target);
                    }
                }
            }
            lastUse = now;
            return target;
        }

        private static void rethrow(Throwable t) {
            if (t instanceof RuntimeException re) throw re;
            if (t instanceof Error er) throw er;
            throw new RuntimeException(t);
        }
    }

    private LazyProxy() {}
}
