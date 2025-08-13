package com.gwngames.core.input.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.ITouchAdapter;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.input.TouchEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.TouchInputIdentifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Touch-screen adapter – keeps one identifier per pointer index so that
 * equality & history work as expected across frames.
 */
@Init(module = ModuleNames.CORE)
public final class TouchInputAdapter extends BaseInputAdapter
    implements ITouchAdapter, InputProcessor {

    private final Map<Integer, TouchInputIdentifier> pointerId = new HashMap<>();

    public TouchInputAdapter() { super("Touchscreen"); }

    @Override public void start() { Gdx.input.setInputProcessor(this); }

    @Override
    public void stop() {
        if (Gdx.input.getInputProcessor() == this)
            Gdx.input.setInputProcessor(null);
    }

    /* ───────────────────── touch events ───────────────────── */

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        dispatch(new TouchEvent(InputType.TOUCH_DOWN,
            getSlot(), id(pointer), new Vector2(sx, sy), 1f));
        return false;
    }

    @Override
    public boolean touchUp(int sx, int sy, int pointer, int button) {
        dispatch(new TouchEvent(InputType.TOUCH_UP,
            getSlot(), id(pointer), new Vector2(sx, sy), 0f));
        return false;
    }

    @Override
    public boolean touchDragged(int sx, int sy, int pointer) {
        dispatch(new TouchEvent(InputType.TOUCH_DRAG,
            getSlot(), id(pointer), new Vector2(sx, sy), 1f));
        return false;
    }

    /* unused InputProcessor parts ------------------------------------- */
    @Override public boolean keyDown(int keycode)            { return false; }
    @Override public boolean keyUp(int keycode)              { return false; }
    @Override public boolean keyTyped(char c)                { return false; }
    @Override public boolean touchCancelled(int x,int y,int p,int b){return false;}
    @Override public boolean mouseMoved(int x,int y)         { return false; }
    @Override public boolean scrolled(float dx,float dy)     { return false; }

    /* helper – returns (and caches) the identifier for a pointer */
    private TouchInputIdentifier id(int pointer) {
        // TODO map screen_zone -> recordWhilePressed
        return pointerId.computeIfAbsent(
            pointer,
            p -> new TouchInputIdentifier(p, true)   // recordWhilePressed = true inside
        );
    }
}
