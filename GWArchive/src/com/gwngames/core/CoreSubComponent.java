package com.gwngames.core;

/**
 * Centralized sub-component identifiers.
 *
 * <p><b>Note:</b> People that will mess up the class structure will be corporally punished.</p>
 */
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
    // Detection
    // ---------------------------------------------------------------------
    public static final String NOOP_DETECTOR        = "NOOP_DETECTOR";
    public static final String CONTROLLER_DETECTOR  = "CONTROLLER_DETECTOR";
    public static final String PERIPHERAL_DETECTOR  = "PERIPHERAL_DETECTOR";

    // ---------------------------------------------------------------------
    // Policies
    // ---------------------------------------------------------------------
    public static final String COMBO_TTL_TRIGGER_POLICY = "COMBO_TTL_TRIGGER_POLICY";

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
    public static final String LOGIC_QUEUE  = "LOGIC_QUEUE";
    public static final String INPUT_QUEUE  = "INPUT_QUEUE";
    public static final String RENDER_QUEUE = "RENDER_QUEUE";
    public static final String SYSTEM_QUEUE = "SYSTEM_QUEUE";

    // ---------------------------------------------------------------------
    // Event / input specialization
    // ---------------------------------------------------------------------
    public static final String EVENT_STATUS_LOGGER = "EVENT_STATUS_LOGGER";
    public static final String INPUT_ACTION_EVENT  = "INPUT_ACTION_EVENT";
}
