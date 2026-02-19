package com.gwngames.core.base.cfg;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.cfg.IContext;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.cfg.ContextKey;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Simple, thread-safe implementation of {@link IContext}. */
@Init(module = CoreModule.CORE)
public class CoreContext extends BaseComponent implements IContext {

    // Values win over providers; providers compute once on first read.
    private final Map<ContextKey<?>, Object>          values    = new ConcurrentHashMap<>();
    private final Map<ContextKey<?>, Supplier<?>>     providers = new ConcurrentHashMap<>();

    @Override
    public <T> void put(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.type().isInstance(value)) {
            throw new ClassCastException("Value " + value + " not of type " + key.type());
        }
        values.put(key, value);
        // keep provider around; value always takes precedence
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object v = values.get(key);
        if (v != null) return (T) v;

        Supplier<?> sup = providers.get(key);
        if (sup != null) {
            // Compute exactly once even under concurrency.
            Object computed = values.computeIfAbsent(key, k -> {
                Object o = sup.get();
                if (o == null) throw new IllegalStateException("Provider returned null for " + key);
                if (!key.type().isInstance(o)) {
                    throw new ClassCastException("Provider returned " + o.getClass() +
                        " for " + key + ", expected " + key.type());
                }
                return o;
            });
            return (T) computed;
        }
        throw new IllegalStateException("Missing context key: " + key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOrDefault(ContextKey<T> key, T defaultValue) {
        Objects.requireNonNull(key, "key");
        Object v = values.get(key);
        if (v != null) return (T) v;

        Supplier<?> sup = providers.get(key);
        if (sup != null) {
            return get(key); // will compute & cache via get()
        }
        return defaultValue;
    }

    @Override
    public <T> void provide(ContextKey<T> key, Supplier<T> supplier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        providers.put(key, supplier);
    }

    @Override
    public boolean contains(ContextKey<?> key) {
        return values.containsKey(key) || providers.containsKey(key);
    }
}
