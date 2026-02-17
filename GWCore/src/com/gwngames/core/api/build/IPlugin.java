package com.gwngames.core.api.build;

import com.gwngames.core.generated.ModulePriorityRegistry;

/**
 * Simple plugin contract loaded via ServiceLoader and resolved by priority.
 * Higher priority wins (derived from module string via ModulePriorityRegistry).
 */
public interface IPlugin {

    /** Unique plugin id (e.g. "test-env", "gdx-test-env"). */
    String id();

    /** Module id string (e.g. DefaultModule.CORE, GameModule.GAME). */
    String module();

    /** Higher priority wins. */
    default int priority() {
        return ModulePriorityRegistry.priorityOf(module());
    }
}
