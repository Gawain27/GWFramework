package com.gwngames.core;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
public enum CoreModule {
    // ---------------------------------------------------------------------
    // Framework modules
    // ---------------------------------------------------------------------
    // Archive is not really needed as JNDI is at CORE, but you never know...
    ARCHIVE("Archive", 0),
    CORE("Core", 5);

    private final String moduleName;
    private final int modulePriority;

    CoreModule(String moduleName, int modulePriority) {
        this.moduleName = moduleName;
        this.modulePriority = modulePriority;
    }

    public String getName() {
        return moduleName;
    }

    public int getPriority() {
        return modulePriority;
    }
}
