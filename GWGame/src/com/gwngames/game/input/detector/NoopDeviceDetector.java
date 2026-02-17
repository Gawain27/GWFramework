package com.gwngames.game.input.detector;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.input.IDeviceDetector;
import com.gwngames.game.api.input.IInputDeviceListener;

@Init(module = GameModule.GAME, subComp = GameSubComponent.NOOP_DETECTOR)
public class NoopDeviceDetector extends BaseComponent implements IDeviceDetector {
    @Override public void start() { }
    @Override public void stop() { }
    @Override public void addDeviceListener(IInputDeviceListener l) { }
    @Override public void removeDeviceListener(IInputDeviceListener l) { }
}
