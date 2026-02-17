package com.gwngames.core;

import com.gwngames.catalog.ModuleCatalog;
import com.gwngames.catalog.ModulePriorities;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
@ModuleCatalog
@ModulePriorities({
    @ModulePriorities.Entry(id = "archive", priority = -1),
    @ModulePriorities.Entry(id = "core", priority = 5),
})
public final class CoreModule {
    private CoreModule() {}

    public static final String ARCHIVE = "archive";
    public static final String CORE = "core";
}
