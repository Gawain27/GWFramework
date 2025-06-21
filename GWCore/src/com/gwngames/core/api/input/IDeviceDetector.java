package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.DEVICE_DETECTOR, allowMultiple = true, module = ModuleNames.INTERFACE)
public interface IDeviceDetector extends IBaseComp{
    void start();
    void stop();
    void addDeviceListener(IInputDeviceListener listener);
    void removeDeviceListener(IInputDeviceListener listener);
}

