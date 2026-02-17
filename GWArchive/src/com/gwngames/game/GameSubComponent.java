package com.gwngames.game;

import com.gwngames.catalog.SubComponentCatalog;

@SubComponentCatalog
public final class GameSubComponent {
    private GameSubComponent(){}

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
    // Queues
    // ---------------------------------------------------------------------
    public static final String LOGIC_QUEUE  = "LOGIC_QUEUE";
    public static final String INPUT_QUEUE  = "INPUT_QUEUE";
    public static final String RENDER_QUEUE = "RENDER_QUEUE";

    // ---------------------------------------------------------------------
    // Event specialization
    // ---------------------------------------------------------------------
    public static final String INPUT_ACTION_EVENT  = "INPUT_ACTION_EVENT";

}
