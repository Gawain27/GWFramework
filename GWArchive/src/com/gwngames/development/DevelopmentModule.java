package com.gwngames.development;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
public enum DevelopmentModule {

    // ---------------------------------------------------------------------
    // Project modules
    // ---------------------------------------------------------------------
    DEVELOPMENT("development", 900);

    private final String moduleName;
    private final int modulePriority;

    DevelopmentModule(String moduleName, int modulePriority) {
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
