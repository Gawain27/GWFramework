package com.gwngames.core.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IAxisEvent;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

/**
 * Represents motion on an analog axis (stick X/Y, trigger, wheel, etc.).
 * {@code rawValue} is the platform-specific reading; {@code normalizedValue}
 * is post-processed (e.g., dead-zone removed, clamped −1‥1) for gameplay use.
 */
@Init(module = ModuleNames.CORE)
public final class AxisEvent extends InputEvent implements IAxisEvent {

    private final IInputIdentifier control;

    /** Raw value straight from the API. */
    private final float rawValue;

    /** Application-level value (e.g. after dead-zone). */
    private final float normalizedValue;

    public AxisEvent(int   slot,
                     IInputIdentifier control,
                     float rawValue,
                     float normalizedValue) {
        super(InputType.AXIS_MOVE, slot, System.nanoTime());

        this.control          = control;
        this.rawValue         = rawValue;
        this.normalizedValue  = normalizedValue;
    }

    /* ───── accessors ───── */

    @Override
    public IInputIdentifier getControl()        { return control; }
    @Override
    public float            getRawValue()       { return rawValue; }
    @Override
    public float            getNormalizedValue(){ return normalizedValue; }

    @Override
    public String toString() {
        return "AXIS[%d] %s raw=%.3f norm=%.3f".formatted(
            getSlot(),
            control.getDisplayName(),
            rawValue,
            normalizedValue);
    }
}

