package com.gwngames.core.data;

public enum SubComponentNames {
    // Special
    NONE,

    // Input Identifiers
    KEY_INPUT,
    CONTROLLER_INPUT,
    TOUCH_INPUT,

    // Startup / System Checks
    CONFIG_CHECK,

    // Event Types
    TOUCH_EVENT,
    BUTTON_EVENT,
    AXIS_EVENT,
    SIMPLE_EVENT,

    // Detection & Adapters
    NOOP_DETECTOR,
    CONTROLLER_DETECTOR,
    PERIPHERAL_DETECTOR,
    CONTROLLER_ADAPTER,
    KEYBOARD_ADAPTER,
    TOUCH_ADAPTER
}
