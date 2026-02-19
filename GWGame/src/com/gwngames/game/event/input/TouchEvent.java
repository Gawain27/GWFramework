package com.gwngames.game.event.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.event.input.ITouchEvent;
import com.gwngames.game.api.input.IInputIdentifier;

/**
 * A rich event representing any touchscreen pointer activity
 * (down, up, drag).  It carries a pointer-specific identifier
 * plus absolute screen coordinates and optional pressure.
 */
@Init(module = GameModule.GAME)
public class TouchEvent extends InputEvent implements ITouchEvent {

    /** Which logical pointer (0‥N) this event came from. */
    private IInputIdentifier control;

    /** Screen position when the event fired (pixel coords). */
    private Vector2 position;

    /** Pressure if available (1 = full press). */
    private float pressure;


    /* ---------------- getters ---------------- */

    @Override
    public IInputIdentifier getControl()  { return control; }
    @Override
    public Vector2          getPosition() { return position; }
    @Override
    public float            getPressure() { return pressure; }

    @Override
    public void setControl(IInputIdentifier control) {
        this.control = control;
    }

    @Override
    public void setPosition(Vector2 position) {
        this.position = position;
    }

    @Override
    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    /* ---------------- convenience ---------------- */

    @Override
    public String toString() {
        return "%s %s @(%d,%d) p=%.2f".formatted(
            getType(), control,
            (int) position.x, (int) position.y,
            pressure);
    }
}

