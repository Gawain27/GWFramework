package com.gwngames.core.data;

public enum ComponentNames {
    // Core system
    CONFIGURATION,
    CONTEXT,
    LAUNCHER,
    SYSTEM,
    TRANSLATOR,
    LOCALE,
    SUBTYPE_REGISTRY,

    // Game lifecycle
    GAME,
    LAUNCHER_MASTER,

    // Asset & file management
    ASSET_MANAGER,
    ASSET_SUBTYPE,
    FILE_EXTENSION,

    // Input system – managers
    INPUT_ADAPTER_MANAGER,
    INPUT_CHAIN_MANAGER,
    INPUT_ACTION_MANAGER,
    INPUT_COMBO_MANAGER,

    // Input system – components
    INPUT_EVENT,
    INPUT_IDENTIFIER,
    INPUT_ADAPTER,
    INPUT_DEVICE_LISTENER,
    INPUT_LISTENER,
    INPUT_ACTION,
    INPUT_COMBO,
    INPUT_CHAIN,
    INPUT_BUFFER,
    INPUT_HISTORY,
    INPUT_MAPPER,

    // Events & macros
    MACRO_EVENT,
    EVENT,

    // Startup
    STARTUP_CHECK,

    // Detectors
    DEVICE_DETECTOR,

    // Special
    NONE;
}
