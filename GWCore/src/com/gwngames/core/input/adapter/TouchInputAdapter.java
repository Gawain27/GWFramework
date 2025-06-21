package com.gwngames.core.input.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.input.TouchEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.api.input.InputType;
import com.gwngames.core.input.controls.TouchInputIdentifier;

@Init(module = ModuleNames.CORE)
public class TouchInputAdapter extends BaseInputAdapter implements IInputAdapter, InputProcessor {

    public TouchInputAdapter() { super("Touchscreen"); }

    /* life-cycle ----------------------------------------------------------- */
    @Override public void start() { Gdx.input.setInputProcessor(this); }

    @Override
    public void stop() {
        if (Gdx.input.getInputProcessor() == this) Gdx.input.setInputProcessor(null);
    }

    /* InputProcessor – touch ---------------------------------------------- */
    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        dispatch(new TouchEvent(InputType.TOUCH_DOWN,
            getSlot(),
            new TouchInputIdentifier(pointer),
            new Vector2(sx, sy),
            1f));
        return false;
    }

    @Override
    public boolean touchUp(int sx, int sy, int pointer, int button) {
        dispatch(new TouchEvent(InputType.TOUCH_UP,
            getSlot(),
            new TouchInputIdentifier(pointer),
            new Vector2(sx, sy),
            0f));
        return false;
    }

    @Override
    public boolean touchCancelled(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override
    public boolean touchDragged(int sx, int sy, int pointer) {
        dispatch(new TouchEvent(InputType.TOUCH_DRAG,
            getSlot(),
            new TouchInputIdentifier(pointer),
            new Vector2(sx, sy),
            1f));
        return false;
    }

    /* unused InputProcessor methods --------------------------------------- */
    @Override public boolean keyDown(int keycode){return false;}
    @Override public boolean keyUp(int keycode){return false;}
    @Override public boolean keyTyped(char character){return false;}
    @Override public boolean mouseMoved(int screenX,int screenY){return false;}
    @Override public boolean scrolled(float amountX,float amountY){return false;}
}
