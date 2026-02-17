package com.gwngames.core;

import com.gwngames.catalog.SubComponentCatalog;

/**
 * Centralized sub-component identifiers.
 *
 * <p><b>Note:</b> People that will mess up the class structure will be corporally punished.</p>
 */
@SubComponentCatalog
public final class CoreSubComponent {
    private CoreSubComponent() {}

    // ---------------------------------------------------------------------
    // Special
    // ---------------------------------------------------------------------
    public static final String NONE         = "NONE";
    public static final String SIMPLE_EVENT = "SIMPLE_EVENT";

    // ---------------------------------------------------------------------
    // Startup / system checks
    // ---------------------------------------------------------------------
    public static final String CONFIG_CHECK = "CONFIG_CHECK";

    // ---------------------------------------------------------------------
    // Dashboard content blocks
    // ---------------------------------------------------------------------
    public static final String DASHBOARD_RAM_CONTENT    = "DASHBOARD_RAM_CONTENT";
    public static final String DASHBOARD_IO_CONTENT     = "DASHBOARD_IO_CONTENT";
    public static final String DASHBOARD_CPU_CONTENT    = "DASHBOARD_CPU_CONTENT";
    public static final String DASHBOARD_LOGS_COMPONENT = "DASHBOARD_LOGS_COMPONENT";

    // ---------------------------------------------------------------------
    // Queues
    // ---------------------------------------------------------------------
    public static final String SYSTEM_QUEUE = "SYSTEM_QUEUE";

    // ---------------------------------------------------------------------
    // Queues
    // ---------------------------------------------------------------------
    public static final String SYSTEM_QUEUE  = "SYSTEM_QUEUE";
    public static final String COMM_QUEUE  = "COMM_QUEUE";

    // ---------------------------------------------------------------------
    // Event specialization
    // ---------------------------------------------------------------------
    public static final String EVENT_STATUS_LOGGER = "EVENT_STATUS_LOGGER";
}
