package com.gwngames.core;

import com.gwngames.catalog.ComponentCatalog;

/**
 * Centralized component identifiers.
 *
 * <p><b>Note:</b> People that will mess up the class structure will be corporally punished.</p>
 */
@ComponentCatalog
public final class CoreComponent {
    private CoreComponent() {}

    // ---------------------------------------------------------------------
    // Core system
    // ---------------------------------------------------------------------
    public static final String CONFIGURATION    = "CONFIGURATION";
    public static final String CONTEXT          = "CONTEXT";
    public static final String LAUNCHER         = "LAUNCHER";
    public static final String SYSTEM           = "SYSTEM";
    public static final String TRANSLATOR       = "TRANSLATOR";
    public static final String LOCALE           = "LOCALE";

    // ---------------------------------------------------------------------
    // Asset & file management
    // ---------------------------------------------------------------------
    public static final String FILE_EXTENSION = "FILE_EXTENSION";

    // ---------------------------------------------------------------------
    // Events & macros
    // ---------------------------------------------------------------------
    public static final String MACRO_EVENT = "MACRO_EVENT";
    public static final String EVENT       = "EVENT";

    // ---------------------------------------------------------------------
    // Startup
    // ---------------------------------------------------------------------
    public static final String STARTUP_CHECK = "STARTUP_CHECK";

    // ---------------------------------------------------------------------
    // Special / Test / framework internals
    // ---------------------------------------------------------------------
    public static final String NONE = "NONE";
    public static final String PIPPO = "PIPPO";
    public static final String POPPO = "POPPO";

    // ---------------------------------------------------------------------
    // Dashboard UI
    // ---------------------------------------------------------------------
    public static final String DASHBOARD               = "DASHBOARD";
    public static final String DASHBOARD_LAYER         = "DASHBOARD_LAYER";
    public static final String DASHBOARD_HEADER        = "DASHBOARD_HEADER";
    public static final String DASHBOARD_CONTENT       = "DASHBOARD_CONTENT";
    public static final String DASHBOARD_ITEM          = "DASHBOARD_ITEM";
    public static final String DASHBOARD_ITEM_CATEGORY = "DASHBOARD_ITEM_CATEGORY";
    public static final String DASHBOARD_CATEGORY      = "DASHBOARD_CATEGORY";
    public static final String DASHBOARD_TABLE         = "DASHBOARD_TABLE";

    // ---------------------------------------------------------------------
    // Platform / runtime plumbing
    // ---------------------------------------------------------------------
    public static final String PATH_RESOLVER       = "PATH_RESOLVER";
    public static final String CLASS_LOADER        = "CLASS_LOADER";

    // ---------------------------------------------------------------------
    // Event Policies
    // ---------------------------------------------------------------------
    public static final String EVAL_TRIGGER_POLICY   = "EVAL_TRIGGER_POLICY";
    public static final String EXECUTION_CONDITION   = "EXECUTION_CONDITION";
    public static final String EXEC_CONDITION_POLICY = "EXEC_CONDITION_POLICY";
    public static final String EXEC_CONDITION_RESULT = "EXEC_CONDITION_RESULT";

    // ---------------------------------------------------------------------
    // Events (specialized / internal)
    // ---------------------------------------------------------------------
    public static final String SYSTEM_EVENT       = "SYSTEM_EVENT";
    public static final String COMM_EVENT         = "COMM_EVENT";
    public static final String EVENT_TRIGGER      = "EVENT_TRIGGER";
    public static final String EVENT_LOGGER       = "EVENT_LOGGER";
    public static final String EVENT_STATUS       = "EVENT_STATUS";
    public static final String EVENT_QUEUE        = "EVENT_QUEUE";
    public static final String MASTER_EVENT_QUEUE = "MASTER_EVENT_QUEUE";

    // ---------------------------------------------------------------------
    // Triggers
    // ---------------------------------------------------------------------
    public static final String MANUAL_TRIGGER = "MANUAL_TRIGGER";
    public static final String TIME_TRIGGER   = "TIME_TRIGGER";

}
