package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

@Init(module = DefaultModule.INTERFACE, allowMultiple = true, component = GameComponent.INPUT_DEVICE_LISTENER)
public interface IInputDeviceListener extends IBaseComp {
    /** Called whenever a new adapter is detected (e.g. controller plugged in). */
    void onAdapterConnected(IInputAdapter adapter);

    /** Called whenever an adapter is removed/disconnected. */
    void onAdapterDisconnected(IInputAdapter adapter);
}
