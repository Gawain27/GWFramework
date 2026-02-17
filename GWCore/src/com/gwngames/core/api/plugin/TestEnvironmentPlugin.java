package com.gwngames.core.api.plugin;

import com.gwngames.core.api.build.IPlugin;

/**
 * Plugin that can install environment hooks required by tests.
 * gwcore defines the contract; gwgame can provide a libGDX-backed impl.
 */
public interface TestEnvironmentPlugin extends IPlugin {

    /**
     * Called once from BaseTest @BeforeAll.
     * Implementations may install stubs, init runtime globals, etc.
     */
    void install();
}
