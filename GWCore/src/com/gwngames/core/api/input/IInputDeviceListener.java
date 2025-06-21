package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.INTERFACE, allowMultiple = true, component = ComponentNames.INPUT_DEVICE_LISTENER)
public interface IInputDeviceListener extends IBaseComp {
    /** Called whenever a new adapter is detected (e.g. controller plugged in). */
    void onAdapterConnected(IInputAdapter adapter);

    /** Called whenever an adapter is removed/disconnected. */
    void onAdapterDisconnected(IInputAdapter adapter);
}
