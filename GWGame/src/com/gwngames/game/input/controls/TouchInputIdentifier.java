package com.gwngames.game.input.controls;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.ITouchIdentifier;

/**
 * Identifier for a particular touch pointer (finger or stylus).
 * Pointer index comes directly from LibGDX InputProcessor callbacks.
 */
@Init(module = GameModule.GAME)
public class TouchInputIdentifier extends BaseInputIdentifier implements ITouchIdentifier {
    private int pointer;  // LibGDX pointer index (0,1,2...)

    public TouchInputIdentifier() {}

    public TouchInputIdentifier(int pointer, boolean recordWhilePressed) {
        super(recordWhilePressed);
        this.pointer = pointer;
    }

    @Override
    public int getPointer() {
        return pointer;
    }
    @Override
    public void setPointer(int pointer) {
        this.pointer = pointer;
    }

    /* ------------- IInputIdentifier metadata ------------- */

    @Override public String getDeviceType()     { return "Touchscreen"; }
    @Override public String getComponentType()  { return "Pointer"; }
    @Override public String getDisplayName()    { return "Pointer " + pointer; }

    /* ------------- equals / hashCode / toString ----------- */

    @Override
    public String toString() { return getDisplayName(); }

    @Override
    public boolean equals(Object o) {
        return (o instanceof TouchInputIdentifier other) && other.pointer == pointer;
    }

    @Override public int hashCode() { return Integer.hashCode(pointer); }
}
