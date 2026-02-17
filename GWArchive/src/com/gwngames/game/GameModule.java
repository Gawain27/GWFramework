package com.gwngames.game;

import com.gwngames.catalog.ModuleCatalog;
import com.gwngames.catalog.ModulePriorities;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
@ModuleCatalog
@ModulePriorities({
    @ModulePriorities.Entry(id = "game", priority = 15)
})
public final class GameModule {
    private GameModule() {}

    public static final String GAME = "game";
}
