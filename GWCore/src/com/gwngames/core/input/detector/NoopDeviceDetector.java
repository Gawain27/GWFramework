package com.gwngames.core.input.detector;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

@Init(module = ModuleNames.CORE)
public class NoopDeviceDetector extends BaseComponent implements IDeviceDetector {
    @Override public void start() { }
    @Override public void stop() { }
    @Override public void addDeviceListener(IInputDeviceListener l) { }
    @Override public void removeDeviceListener(IInputDeviceListener l) { }
}
