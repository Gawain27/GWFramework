package com.gwngames;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
public enum DefaultModule {

    // ---------------------------------------------------------------------
    // Special / framework meta
    // ---------------------------------------------------------------------
    UNIMPLEMENTED("Unimplemented", 0),
    INTERFACE("Interface", 1),

    // ---------------------------------------------------------------------
    // Testing
    // ---------------------------------------------------------------------
    TEST("Test", 9999);

    private final String moduleName;
    private final int modulePriority;

    DefaultModule(String moduleName, int modulePriority) {
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
