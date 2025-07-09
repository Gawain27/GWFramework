package com.gwngames.core.input.controls;

import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;

public abstract class BaseInputIdentifier extends BaseComponent implements IInputIdentifier {
    private final boolean recordWhilePressed;
    public BaseInputIdentifier(boolean recordWhilePressed){
        this.recordWhilePressed = recordWhilePressed;
    }

    @Override
    public boolean isRecordWhilePressed() {
        return recordWhilePressed;
    }
}
