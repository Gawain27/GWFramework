package com.gwngames.core.api.base.cfg;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.cfg.ContextKey;
import com.gwngames.game.GameModule;

import java.util.function.Supplier;

/**
 * Minimal, type-safe application context with optional lazy providers.
 * Keep it straightforward: put/get, getOrDefault, provide, contains.
 */
@Init(component = CoreComponent.CONTEXT, module = DefaultModule.INTERFACE)
public interface IContext extends IBaseComp {

    // do not mess with these objects or you will break game startup
    // UNLESS YOU KNOW WHAT YOU ARE DOING!!! Which you don't.
    ContextKey<Object> _LAUNCHER    = ContextKey.of("launcher", Object.class);
    ContextKey<Object> _DIRECTOR    = ContextKey.of("director", Object.class);

    // Common keys (replace/extend as you like)

    /** Store a value (overrides any provider for the same key). */
    <T> void put(ContextKey<T> key, T value);

    /** Retrieve a value; if a provider exists and no value yet, compute once and cache. */
    <T> T get(ContextKey<T> key);

    /** Retrieve a value or default. Will compute from provider if present. */
    <T> T getOrDefault(ContextKey<T> key, T defaultValue);

    /** Register a provider: computed lazily on first {@link #get} and cached. */
    <T> void provide(ContextKey<T> key, Supplier<T> supplier);

    /** Whether a value or provider exists for this key. */
    boolean contains(ContextKey<?> key);
}
