package com.gwngames.core.event.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.ITouchEvent;
import com.gwngames.core.data.input.InputType;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.ModuleNames;

/**
 * A rich event representing any touchscreen pointer activity
 * (down, up, drag).  It carries a pointer-specific identifier
 * plus absolute screen coordinates and optional pressure.
 */
@Init(module = ModuleNames.CORE)
public final class TouchEvent extends InputEvent implements ITouchEvent {

    /** Which logical pointer (0‥N) this event came from. */
    private final IInputIdentifier control;

    /** Screen position when the event fired (pixel coords). */
    private final Vector2 position;

    /** Pressure if available (1 = full press). */
    private final float pressure;

    public TouchEvent(InputType type,
                      int slot,
                      IInputIdentifier control,
                      Vector2 position,
                      float pressure) {
        super(type, slot, System.nanoTime());
        this.control  = control;
        this.position = position;
        this.pressure = pressure;
    }

    /* ---------------- getters ---------------- */

    @Override
    public IInputIdentifier getControl()  { return control; }
    @Override
    public Vector2          getPosition() { return position; }
    @Override
    public float            getPressure() { return pressure; }

    /* ---------------- convenience ---------------- */

    @Override
    public String toString() {
        return "%s %s @(%d,%d) p=%.2f".formatted(
            getType(), control,
            (int) position.x, (int) position.y,
            pressure);
    }
}

