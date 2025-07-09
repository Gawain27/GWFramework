package com.gwngames.core.base;

import com.gwngames.core.api.base.IBaseComp;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lightweight dynamic proxy that
 * <ul>
 *   <li>creates the real instance on first access (lazy)</li>
 *   <li>evicts it after {@link #TTL_MS} milliseconds of idle time
 *       unless {@code immortal == true}</li>
 *   <li>supports JDK&nbsp;8&nbsp;+ default interface methods</li>
 * </ul>
 */
public final class LazyProxy {

    /* ───────────────────────── configuration ───────────────────────── */

    /** Idle time in milliseconds before a non-immortal instance is evicted. */
    private static volatile long TTL_MS =
        Long.getLong("gw.lazy.ttl", 5 * 60_000);     // 5 min default

    public static void setTtl(long ttlMillis) { TTL_MS = ttlMillis; }

    /* ───────────────────────── factory helpers ─────────────────────── */

    public static <T extends IBaseComp> T of(Class<T> iface,
                                             Supplier<T> supplier,
                                             boolean immortal) {
        if (!iface.isInterface()) {          // plain class – no proxy required
            return supplier.get();
        }

        @SuppressWarnings("unchecked")
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

        private volatile WeakReference<T> ref = new WeakReference<>(null);
        private volatile long lastUse = System.currentTimeMillis();

        Handler(Supplier<T> supplier, boolean immortal) {
            this.supplier = supplier;
            this.immortal = immortal;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {

            /* Object overrides never forward -------------------------------- */
            switch (m.getName()) {
                case "hashCode" -> { return System.identityHashCode(proxy); }
                case "equals"   -> { return proxy == args[0]; }
                case "toString" -> { return "LazyProxy<" + supplier + ">"; }
            }

            /* resolve or recreate the real target -------------------------- */
            long   now    = System.currentTimeMillis();
            T      target = materialiseTarget(now);

            /* JDK 8 / 9 default-method support ----------------------------- */
            if (m.isDefault()) {
                Class<?> declaring = m.getDeclaringClass();
                MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());

                MethodHandle handle = lookup.findSpecial(
                    declaring,
                    m.getName(),
                    MethodType.methodType(m.getReturnType(), m.getParameterTypes()),
                    declaring);

                return handle.bindTo(target)
                    .invokeWithArguments(args == null ? new Object[0] : args);
            }

            /* ordinary interface method – ensure accessibility ------------- */
            if (!m.canAccess(target)) {      // JDK 9 convenience
                m.setAccessible(true);
            }
            return m.invoke(target, args);
        }

        /**
         * Returns a live target, recreating it if evicted or not yet created.
         */
        private T materialiseTarget(long now) {
            T target = ref.get();

            /* idle-time eviction */
            if (!immortal && target != null && now - lastUse > TTL_MS) {
                ref.clear();
                target = null;
            }

            /* lazy creation (double-checked within the handler lock) */
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
            return target;
        }
    }

    private LazyProxy() {}   // utility class – no instances
}
