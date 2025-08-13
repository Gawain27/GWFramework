package com.gwngames.core.data.cfg;

import com.gwngames.core.api.base.cfg.IContext;

import java.util.Objects;

/** A typed key for values stored in {@link IContext}. */
public final class ContextKey<T> {
    private final String name;
    private final Class<T> type;

    private ContextKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> ContextKey<T> of(String name, Class<T> type) {
        return new ContextKey<>(name, type);
    }

    public String name() { return name; }
    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextKey<?> k)) return false;
        return name.equals(k.name) && type.equals(k.type);
    }
    @Override public int hashCode() { return Objects.hash(name, type); }
    @Override public String toString() { return "ContextKey(" + name + ":" + type.getSimpleName() + ")"; }
}
