package com.gwngames.core.input.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.KeyInputIdentifier;

@Init(module = ModuleNames.CORE)
public class KeyboardInputAdapter extends BaseInputAdapter implements IInputAdapter, InputProcessor {

    public KeyboardInputAdapter() { super("Keyboard"); }

    /* life-cycle ----------------------------------------------------------- */
    @Override public void start() { Gdx.input.setInputProcessor(this); }

    @Override
    public void stop() {
        if (Gdx.input.getInputProcessor() == this) Gdx.input.setInputProcessor(null);
    }

    /* InputProcessor – keys ----------------------------------------------- */
    @Override
    public boolean keyDown(int keycode) {
        dispatch(new ButtonEvent(getSlot(),
            new KeyInputIdentifier(keycode),
            true, 1f));
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        dispatch(new ButtonEvent(getSlot(),
            new KeyInputIdentifier(keycode),
            false, 0f));
        return false;
    }

    /* we ignore the rest --------------------------------------------------- */
    @Override public boolean keyTyped(char c)                         { return false; }
    @Override public boolean touchDown (int sx,int sy,int p,int b)    { return false; }
    @Override public boolean touchUp   (int sx,int sy,int p,int b)    { return false; }

    @Override
    public boolean touchCancelled(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override public boolean touchDragged(int sx,int sy,int p)        { return false; }
    @Override public boolean mouseMoved(int sx,int sy)                { return false; }
    @Override public boolean scrolled(float dx,float dy)              { return false; }
}

