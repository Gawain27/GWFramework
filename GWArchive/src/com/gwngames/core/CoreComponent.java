package com.gwngames.core;

/**
 * Centralized component identifiers.
 *
 * <p><b>Note:</b> People that will mess up the class structure will be corporally punished.</p>
 */
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
    public static final String SUBTYPE_REGISTRY = "SUBTYPE_REGISTRY";

    // ---------------------------------------------------------------------
    // Game lifecycle
    // ---------------------------------------------------------------------
    public static final String GAME            = "GAME";
    public static final String LAUNCHER_MASTER = "LAUNCHER_MASTER";

    // ---------------------------------------------------------------------
    // Asset & file management
    // ---------------------------------------------------------------------
    public static final String ASSET_MANAGER  = "ASSET_MANAGER";
    public static final String ASSET_SUBTYPE  = "ASSET_SUBTYPE";
    public static final String FILE_EXTENSION = "FILE_EXTENSION";

    // ---------------------------------------------------------------------
    // Input identifiers
    // ---------------------------------------------------------------------
    public static final String KEY_INPUT               = "KEY_INPUT";
    public static final String CONTROLLER_AXIS_INPUT   = "CONTROLLER_AXIS_INPUT";
    public static final String CONTROLLER_BUTTON_INPUT = "CONTROLLER_BUTTON_INPUT";
    public static final String TOUCH_INPUT             = "TOUCH_INPUT";

    // ---------------------------------------------------------------------
    // Event types
    // ---------------------------------------------------------------------
    public static final String TOUCH_EVENT  = "TOUCH_EVENT";
    public static final String BUTTON_EVENT = "BUTTON_EVENT";
    public static final String AXIS_EVENT   = "AXIS_EVENT";

    // ---------------------------------------------------------------------
    // Adapters
    // ---------------------------------------------------------------------
    public static final String CONTROLLER_ADAPTER = "CONTROLLER_ADAPTER";
    public static final String KEYBOARD_ADAPTER   = "KEYBOARD_ADAPTER";
    public static final String TOUCH_ADAPTER      = "TOUCH_ADAPTER";

    // ---------------------------------------------------------------------
    // Input system – managers
    // ---------------------------------------------------------------------
    public static final String INPUT_ADAPTER_MANAGER = "INPUT_ADAPTER_MANAGER";
    public static final String INPUT_CHAIN_MANAGER   = "INPUT_CHAIN_MANAGER";
    public static final String INPUT_ACTION_MANAGER  = "INPUT_ACTION_MANAGER";
    public static final String INPUT_COMBO_MANAGER   = "INPUT_COMBO_MANAGER";

    // ---------------------------------------------------------------------
    // Input system – components
    // ---------------------------------------------------------------------
    public static final String INPUT_EVENT           = "INPUT_EVENT";
    public static final String INPUT_IDENTIFIER      = "INPUT_IDENTIFIER";
    public static final String INPUT_ADAPTER         = "INPUT_ADAPTER";
    public static final String INPUT_DEVICE_LISTENER = "INPUT_DEVICE_LISTENER";
    public static final String INPUT_LISTENER        = "INPUT_LISTENER";
    public static final String INPUT_ACTION          = "INPUT_ACTION";
    public static final String INPUT_COMBO           = "INPUT_COMBO";
    public static final String INPUT_CHAIN           = "INPUT_CHAIN";
    public static final String INPUT_BUFFER          = "INPUT_BUFFER";
    public static final String INPUT_HISTORY         = "INPUT_HISTORY";
    public static final String INPUT_MAPPER          = "INPUT_MAPPER";

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
    // Detectors
    // ---------------------------------------------------------------------
    public static final String DEVICE_DETECTOR = "DEVICE_DETECTOR";

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
    // Policies
    // ---------------------------------------------------------------------
    public static final String EVAL_TRIGGER_POLICY   = "EVAL_TRIGGER_POLICY";
    public static final String EXECUTION_CONDITION   = "EXECUTION_CONDITION";
    public static final String EXEC_CONDITION_POLICY = "EXEC_CONDITION_POLICY";
    public static final String EXEC_CONDITION_RESULT = "EXEC_CONDITION_RESULT";

    // ---------------------------------------------------------------------
    // Input (specialized)
    // ---------------------------------------------------------------------
    public static final String INPUT_COORDINATOR     = "INPUT_COORDINATOR";
    public static final String INPUT_TELEMETRY       = "INPUT_TELEMETRY";
    public static final String INPUT_MANAGER         = "INPUT_MANAGER";
    public static final String INPUT_ADAPTER_FACTORY = "INPUT_ADAPTER_FACTORY";

    // ---------------------------------------------------------------------
    // Events (specialized / internal)
    // ---------------------------------------------------------------------
    public static final String LOGIC_EVENT        = "LOGIC_EVENT";
    public static final String SYSTEM_EVENT       = "SYSTEM_EVENT";
    public static final String COMM_EVENT         = "COMM_EVENT";
    public static final String RENDER_EVENT       = "RENDER_EVENT";
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
