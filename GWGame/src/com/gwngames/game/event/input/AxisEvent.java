package com.gwngames.game.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.event.input.IAxisEvent;
import com.gwngames.game.api.input.IInputIdentifier;

/**
 * Represents motion on an analog axis (stick X/Y, trigger, wheel, etc.).
 * {@code rawValue} is the platform-specific reading; {@code normalizedValue}
 * is post-processed (e.g., dead-zone removed, clamped −1‥1) for gameplay use.
 */
@Init(module = GameModule.GAME)
public class AxisEvent extends InputEvent implements IAxisEvent {

    private IInputIdentifier control;

    /** Raw value straight from the API. */
    private float rawValue;

    /** Application-level value (e.g. after dead-zone). */
    private float normalizedValue;


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

    @Override
    public void setControl(IInputIdentifier control) {
        this.control = control;
    }

    @Override
    public void setRawValue(float rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public void setNormalizedValue(float normalizedValue) {
        this.normalizedValue = normalizedValue;
    }
}

