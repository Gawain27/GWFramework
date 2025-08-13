package com.gwngames.core.api.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.KEY_INPUT)
public interface IKeyIdentifier extends IInputIdentifier{
    int getKeycode();
}
