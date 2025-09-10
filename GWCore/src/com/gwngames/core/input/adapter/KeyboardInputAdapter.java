package com.gwngames.core.input.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IKeyboardAdapter;
import com.gwngames.core.data.input.IdentifierDefinition;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.KeyInputIdentifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Keyboard → GW events adapter with zero allocations at run-time.
 */
@Init(module = ModuleNames.CORE)
public class KeyboardInputAdapter extends BaseInputAdapter implements IKeyboardAdapter, InputProcessor {

    private final Map<Integer, KeyInputIdentifier> keyId = new HashMap<>();

    public KeyboardInputAdapter() {
        /*  build a canonical map from IdentifierDefinition ---------- */
        for (IdentifierDefinition def : IdentifierDefinition.values()) {
            for (IInputIdentifier raw : def.ids()) {
                if (raw instanceof KeyInputIdentifier k) {
                    keyId.putIfAbsent(k.getKeycode(), k);
                }
            }
        }
    }

    @Override public void start() { Gdx.input.setInputProcessor(this); }

    @Override
    public void stop() {
        if (Gdx.input.getInputProcessor() == this)
            Gdx.input.setInputProcessor(null);
    }

    @Override
    public String getAdapterName() {
        return "Keyboard";
    }

    /* ───────────────────── InputProcessor (keys) ───────────────────── */
    @Override
    public boolean keyDown(int keycode) {
        inputManager.emitButtonDown(this, resolve(keycode), 1f);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        inputManager.emitButtonUp(this, resolve(keycode), 0f);
        return false;
    }

    /* unused callbacks ------------------------------------------------ */
    @Override public boolean keyTyped(char c)             { return false; }
    @Override public boolean touchDown(int x,int y,int p,int b){ return false; }
    @Override public boolean touchUp  (int x,int y,int p,int b){ return false; }
    @Override public boolean touchCancelled(int i,int i1,int i2,int i3){return false;}
    @Override public boolean touchDragged(int x,int y,int p){ return false; }
    @Override public boolean mouseMoved(int x,int y)      { return false; }
    @Override public boolean scrolled(float dx,float dy)  { return false; }

    /* helper – canonical or lazily-created identifier */
    private KeyInputIdentifier resolve(int keycode) {
        return keyId.computeIfAbsent(
            keycode,
            kc -> new KeyInputIdentifier(kc, false));
    }
}
