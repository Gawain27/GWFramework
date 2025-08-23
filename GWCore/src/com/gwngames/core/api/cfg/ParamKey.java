package com.gwngames.core.api.cfg;

import com.gwngames.core.util.ParamRegistry;

import java.util.Objects;

/** Default implementation of a typed parameter key.<br>
 * DO NOT USE ParamKey arbitrarily, resort to IParam, if possible
 * */
public final class ParamKey<T> implements IParam<T> {
    private final String name;
    private final Class<T> type;
    private final boolean user;
    private final boolean nullable;

    private ParamKey(String name, Class<T> type, boolean user, boolean nullable) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.user = user;
        this.nullable = nullable;
        ParamRegistry.register(this); // auto-register on creation
    }

    // factory helpers (sane defaults = not user-modifiable, not nullable)
    public static <T> ParamKey<T> of(String key, Class<T> type) {
        return new ParamKey<>(key, type, false, false);
    }
    public static <T> ParamKey<T> userOf(String key, Class<T> type) {
        return new ParamKey<>(key, type, true, false);
    }
    public static <T> ParamKey<T> nullableOf(String key, Class<T> type) {
        return new ParamKey<>(key, type, false, true);
    }
    public static <T> ParamKey<T> userNullableOf(String key, Class<T> type) {
        return new ParamKey<>(key, type, true, true);
    }

    // optional builder for readability
    public static <T> Builder<T> builder(String key, Class<T> type) {
        return new Builder<>(key, type);
    }
    public static final class Builder<T> {
        private final String key; private final Class<T> type;
        private boolean user; private boolean nullable;
        private Builder(String key, Class<T> type) { this.key = key; this.type = type; }
        public Builder<T> userModifiable(boolean v){ this.user = v; return this; }
        public Builder<T> nullable(boolean v)       { this.nullable = v; return this; }
        public ParamKey<T> build() { return new ParamKey<>(key, type, user, nullable); }
    }

    @Override public String key()           { return name; }
    @Override public Class<T> type()        { return type; }
    @Override public boolean userModifiable(){ return user; }
    @Override public boolean nullable()     { return nullable; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParamKey<?> other)) return false;
        return name.equals(other.name) && type.equals(other.type);
    }
    @Override public int hashCode() { return 31 * name.hashCode() + type.hashCode(); }
    @Override public String toString() {
        return "ParamKey(" + name + ":" + type.getSimpleName()
            + ", user=" + user + ", nullable=" + nullable + ")";
    }
}
