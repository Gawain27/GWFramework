package com.gwngames.game;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
public enum GameModule {

    // ---------------------------------------------------------------------
    // Framework modules
    // ---------------------------------------------------------------------
    GAME("Game", 15);

    private final String moduleName;
    private final int modulePriority;

    GameModule(String moduleName, int modulePriority) {
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
