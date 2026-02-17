package com.gwngames.game;

import com.gwngames.catalog.ComponentCatalog;

@ComponentCatalog
public final class GameComponent {
    private GameComponent (){};

    // ---------------------------------------------------------------------
    // Asset & file management
    // ---------------------------------------------------------------------
    public static final String ASSET_MANAGER  = "ASSET_MANAGER";
    public static final String ASSET_SUBTYPE  = "ASSET_SUBTYPE";
    public static final String SUBTYPE_REGISTRY = "SUBTYPE_REGISTRY";

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
    // Input (specialized)
    // ---------------------------------------------------------------------
    public static final String INPUT_COORDINATOR     = "INPUT_COORDINATOR";
    public static final String INPUT_TELEMETRY       = "INPUT_TELEMETRY";
    public static final String INPUT_MANAGER         = "INPUT_MANAGER";
    public static final String INPUT_ADAPTER_FACTORY = "INPUT_ADAPTER_FACTORY";

    // ---------------------------------------------------------------------
    // Detectors
    // ---------------------------------------------------------------------
    public static final String DEVICE_DETECTOR = "DEVICE_DETECTOR";

    // ---------------------------------------------------------------------
    // Events (specialized / internal)
    // ---------------------------------------------------------------------
    public static final String LOGIC_EVENT        = "LOGIC_EVENT";
    public static final String RENDER_EVENT       = "RENDER_EVENT";

    // ---------------------------------------------------------------------
    // Game lifecycle
    // ---------------------------------------------------------------------
    public static final String GAME            = "GAME";

}
