package com.gwngames.core.api.base.cfg;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.api.cfg.ParamKey;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.ParamRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Init(component = ComponentNames.CONFIGURATION, module = ModuleNames.INTERFACE)
public interface IConfig extends IBaseComp {
    /** Loads parameters and eventually sets defaults.<br>
     * Implementations should call registerParameters() once with required defaults. */
    void registerParameters();

    /* default storage with strict typing */
    Map<IParam<?>, Object> _values = new ConcurrentHashMap<>();

    default <T> void set(IParam<T> key, T value) {
        Objects.requireNonNull(key, "key");
        if (value == null && !key.nullable())
            throw new IllegalStateException("Param not nullable: " + key);
        if (value != null && !key.type().isInstance(value))
            throw new ClassCastException("Wrong type for " + key + ": " + value.getClass());
        _values.put(key, value);
    }

    default <T> void setDefault(IParam<T> key, T value) {
        Objects.requireNonNull(key, "key");
        if (value == null && !key.nullable())
            throw new IllegalStateException("Default not nullable: " + key);
        _values.putIfAbsent(key, value);
    }

    @SuppressWarnings("unchecked")
    default <T> T get(IParam<T> key) {
        Object v = _values.get(key);
        if (v == null) {
            if (key.nullable()) return null;
            throw new IllegalStateException("Missing param: " + key);
        }
        if (!key.type().isInstance(v))
            throw new ClassCastException("Stored value type mismatch for " + key);
        return (T) v;
    }

    default <T> T getOrDefault(IParam<T> key, T fallback) {
        T v = getNullable(key);
        return v != null ? v : fallback;
    }

    @SuppressWarnings("unchecked")
    default <T> T getNullable(IParam<T> key) {
        Object v = _values.get(key);
        if (v == null) return null;
        if (!key.type().isInstance(v))
            throw new ClassCastException("Stored value type mismatch for " + key);
        return (T) v;
    }

    default boolean has(IParam<?> key) { return _values.containsKey(key); }
    default Map<IParam<?>, Object> snapshotAll() { return Collections.unmodifiableMap(_values); }

    /** Call after {@link #registerParameters()} to enforce required params. */
    default void validateAllParamsFilled() {
        List<IParam<?>> missing = new ArrayList<>();
        for (IParam<?> k : ParamRegistry.all()) {
            if (!k.nullable() && !this.has(k)) {
                missing.add(k);
            }
        }
        if (!missing.isEmpty()) {
            String msg = "Missing required configuration parameters: " +
                missing.stream().map(IParam::key).collect(Collectors.joining(", "));
            throw new IllegalStateException(msg);
        }
    }
}
