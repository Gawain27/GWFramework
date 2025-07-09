package com.gwngames.core.api.base;

/**
 * Root marker for every framework component (including enums).
 */
public interface IBaseComp {

    /**
     * Monotonically-increasing identifier that the framework assigns
     * <strong>per instance&nbsp;/ enum constant</strong>.
     * * Components that extend {@code BaseComponent} get this for free.
     * * Enum components should simply return {@code ordinal()} (or any other
     *   constant you prefer).
     */
    int getMultId();
}
