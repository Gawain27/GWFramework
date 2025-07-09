package com.gwngames.core.input.controls;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

/**
 * Identifier for a particular touch pointer (finger or stylus).
 * Pointer index comes directly from LibGDX InputProcessor callbacks.
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.TOUCH_INPUT)
public class TouchInputIdentifier extends BaseInputIdentifier {

    private final int pointer;  // LibGDX pointer index (0,1,2...)

    public TouchInputIdentifier(int pointer, boolean recordWhilePressed) {
        super(recordWhilePressed);
        this.pointer = pointer;
    }

    public int getPointer() { return pointer; }

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
