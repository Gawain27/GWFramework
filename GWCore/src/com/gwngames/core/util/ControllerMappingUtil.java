package com.gwngames.core.util;

import java.util.Map;

public final class ControllerMappingUtil {
    private static final Map<Integer, String> XBOX_BUTTONS = Map.of(
        0, "A",
        1, "B",
        2, "X",
        3, "Y",
        4, "Left Bumper",
        5, "Right Bumper",
        6, "Back",
        7, "Start",
        8, "Left Stick Press",
        9, "Right Stick Press"
    );
    private static final Map<Integer, String> XBOX_AXES = Map.of(
        0, "Left Stick X",
        1, "Left Stick Y",
        2, "Right Stick X",
        3, "Right Stick Y",
        4, "Left Trigger",
        5, "Right Trigger"
    );

    private ControllerMappingUtil() { /* no instances */ }

    public static String getButtonName(String controllerName, int buttonCode) {
        String lower = controllerName.toLowerCase();
        if (lower.contains("xbox")) {
            return XBOX_BUTTONS.getOrDefault(buttonCode, "Button " + buttonCode);
        }
        // fallback for unknown controllers
        return "Button " + buttonCode;
    }

    public static String getAxisName(String controllerName, int axisCode) {
        String lower = controllerName.toLowerCase();
        if (lower.contains("xbox")) {
            return XBOX_AXES.getOrDefault(axisCode, "Axis " + axisCode);
        }
        return "Axis " + axisCode;
    }
}
