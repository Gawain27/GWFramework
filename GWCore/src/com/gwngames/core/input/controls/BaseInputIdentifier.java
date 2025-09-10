package com.gwngames.core.input.controls;

import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.base.BaseComponent;

public abstract class BaseInputIdentifier extends BaseComponent implements IInputIdentifier {
    private boolean recordWhilePressed;

    public BaseInputIdentifier() {}

    public BaseInputIdentifier(boolean recordWhilePressed) {
        this.recordWhilePressed = recordWhilePressed;
    }

    @Override
    public boolean isRecordWhilePressed() {
        return recordWhilePressed;
    }
    @Override
    public void setRecordWhilePressed(boolean isRecordWhilePressed){
        this.recordWhilePressed = isRecordWhilePressed;
    }
}
