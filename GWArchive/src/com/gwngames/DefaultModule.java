package com.gwngames;

import com.gwngames.catalog.ModuleCatalog;
import com.gwngames.catalog.ModulePriorities;

/**
 * Module identifiers used for wiring/overrides and priority-based resolution.
 */
@ModulePriorities({
    @ModulePriorities.Entry(id = "unimplemented", priority = 0),
    @ModulePriorities.Entry(id = "interface", priority = 1),
    @ModulePriorities.Entry(id = "test", priority = 9999)
})
@ModuleCatalog
public final class DefaultModule {
    private DefaultModule() {}

    // ---------------------------------------------------------------------
    // Special / framework meta
    // ---------------------------------------------------------------------
    public static final String INTERFACE = "interface";
    public static final String UNIMPLEMENTED = "unimplemented";

    // ---------------------------------------------------------------------
    // Testing
    // ---------------------------------------------------------------------
    public static final String TEST = "test";

}
