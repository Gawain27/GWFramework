package com.gwngames.development;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */

import com.gwngames.catalog.ModuleCatalog;
import com.gwngames.catalog.ModulePriorities;

@ModuleCatalog
@ModulePriorities({
    @ModulePriorities.Entry(id = "development", priority = 99),
})
public final class DevelopmentModule {
    private DevelopmentModule() {}

    public static final String DEVELOPMENT = "development";
}
