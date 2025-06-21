package com.gwngames.core.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

/**
 * Fired when a digital or analog button / trigger changes state.
 * For game-controller triggers you can use {@code pressure} to
 * convey the analog value (0‥1).  Keyboard keys simply use 0 or 1.
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.BUTTON_EVENT)
public final class ButtonEvent extends InputEvent {

    /** Which physical control generated the event. */
    private final IInputIdentifier control;

    /** {@code true} if pressed, {@code false} if released. */
    private final boolean pressed;

    /** Analog pressure (0 = none, 1 = full). */
    private final float pressure;

    public ButtonEvent(int slot,
                       IInputIdentifier control,
                       boolean pressed,
                       float pressure) {
        super(pressed ? InputType.BUTTON_DOWN : InputType.BUTTON_UP,
            slot,
            System.nanoTime());

        this.control  = control;
        this.pressed  = pressed;
        this.pressure = pressure;
    }

    /* ───── accessors ───── */

    public IInputIdentifier getControl() { return control; }
    public boolean          isPressed()  { return pressed; }
    public float            getPressure(){ return pressure; }

    @Override
    public String toString() {
        return "%s[%d] %s %s p=%.2f".formatted(
            getType(), getSlot(),
            control.getDisplayName(),
            pressed ? "DOWN" : "UP",
            pressure);
    }
}
