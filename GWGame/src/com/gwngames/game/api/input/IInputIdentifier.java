package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.INPUT_IDENTIFIER, module = DefaultModule.INTERFACE, allowMultiple = true)
public interface IInputIdentifier extends IBaseComp {
    /** e.g. "Keyboard", "Xbox Controller", "Touchscreen" */
    String getDeviceType();

    /** e.g. "Key", "Button", "Axis", "Touch Pointer" */
    String getComponentType();

    /** e.g. "Q", "Y Button", "Left Stick X", "Pointer 1" */
    String getDisplayName();

    boolean isRecordWhilePressed();
    void setRecordWhilePressed(boolean isRecordWhilePressed);
}
