package com.gwngames.core.input.controls;

import com.badlogic.gdx.Input;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IKeyIdentifier;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE)
public class KeyInputIdentifier extends BaseInputIdentifier implements IKeyIdentifier {
    private final int keycode;

    public KeyInputIdentifier(int keycode, boolean recordWhilePressed) {
        super(recordWhilePressed);
        this.keycode = keycode;
    }

    @Override
    public int getKeycode() {
        return keycode;
    }

    @Override
    public String getDeviceType() {
        return "Keyboard";
    }

    @Override
    public String getComponentType() {
        return "Key";
    }

    @Override
    public String getDisplayName() {
        // e.g. "Q", "SPACE", "ENTER"
        return Input.Keys.toString(keycode);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof KeyInputIdentifier other)
            && other.keycode == keycode;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(keycode);
    }
}
