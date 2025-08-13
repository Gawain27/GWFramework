package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_IDENTIFIER, module = ModuleNames.INTERFACE, allowMultiple = true)
public interface IInputIdentifier extends IBaseComp {
    /** e.g. "Keyboard", "Xbox Controller", "Touchscreen" */
    String getDeviceType();

    /** e.g. "Key", "Button", "Axis", "Touch Pointer" */
    String getComponentType();

    /** e.g. "Q", "Y Button", "Left Stick X", "Pointer 1" */
    String getDisplayName();

    boolean isRecordWhilePressed();
}
