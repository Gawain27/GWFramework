package com.gwngames.game.event.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IButtonEvent;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ModuleNames;

/**
 * Fired when a digital or analog button / trigger changes state.
 * For game-controller triggers you can use {@code pressure} to
 * convey the analog value (0‥1).  Keyboard keys simply use 0 or 1.
 */
@Init(module = ModuleNames.CORE)
public class ButtonEvent extends InputEvent implements IButtonEvent {

    /** Which physical control generated the event. */
    private IInputIdentifier control;

    /** {@code true} if pressed, {@code false} if released. */
    private boolean pressed;

    /** Analog pressure (0 = none, 1 = full). */
    private float pressure;

    /* ───── accessors ───── */

    @Override
    public IInputIdentifier getControl() { return control; }
    @Override
    public boolean          isPressed()  { return pressed; }
    @Override
    public float            getPressure(){ return pressure; }

    @Override
    public String toString() {
        return "%s[%d] %s %s p=%.2f".formatted(
            getType(), getSlot(),
            control.getDisplayName(),
            pressed ? "DOWN" : "UP",
            pressure);
    }
    @Override
    public void setControl(IInputIdentifier control) {
        this.control = control;
    }

    @Override
    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    @Override
    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

}
