package com.gwngames.core.api.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.TOUCH_INPUT)
public interface ITouchIdentifier extends IInputIdentifier{
    int getPointer();
}
