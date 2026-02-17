package com.gwngames.starter;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
public enum StarterModule {

    // ---------------------------------------------------------------------
    // Runtime / launcher
    // ---------------------------------------------------------------------
    STARTER("Starter", 1000);

    private final String moduleName;
    private final int modulePriority;

    StarterModule(String moduleName, int modulePriority) {
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
