package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(component = GameComponent.DEVICE_DETECTOR, allowMultiple = true, module = DefaultModule.INTERFACE)
public interface IDeviceDetector extends IBaseComp{
    void start();
    void stop();
    void addDeviceListener(IInputDeviceListener listener);
    void removeDeviceListener(IInputDeviceListener listener);
}

