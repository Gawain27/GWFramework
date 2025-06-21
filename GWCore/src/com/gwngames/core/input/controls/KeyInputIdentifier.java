package com.gwngames.core.input.controls;

import com.badlogic.gdx.Input;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.KEY_INPUT)
public class KeyInputIdentifier extends BaseComponent implements IInputIdentifier {
    private final int keycode;

    public KeyInputIdentifier(int keycode) {
        this.keycode = keycode;
    }

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
