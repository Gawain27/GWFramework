package com.gwngames.starter;

import com.gwngames.catalog.ModuleCatalog;
import com.gwngames.catalog.ModulePriorities;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
@ModulePriorities({
    @ModulePriorities.Entry(id = "starter", priority = 999),
})
@ModuleCatalog
public final class StarterModule {
    private StarterModule() {}

    public static final String STARTER = "starter";
}
