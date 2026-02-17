package com.gwngames.game.input.detector;

import com.badlogic.gdx.controllers.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.input.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Init(module = GameModule.GAME, subComp = GameSubComponent.CONTROLLER_DETECTOR)
public class ControllerDeviceDetector extends BaseComponent implements IDeviceDetector, ControllerListener {
    @Inject
    private IInputAdapterFactory factory;

    private final Map<Controller, IInputAdapter> map = new ConcurrentHashMap<>();
    private final List<IInputDeviceListener> listeners = new CopyOnWriteArrayList<>();

    @Override public void start() { Controllers.addListener(this); }

    @Override public void stop()  { Controllers.removeListener(this); }

    /* ─── IDeviceDetector ───────────────────────────── */

    @Override public void addDeviceListener(IInputDeviceListener l){listeners.add(l);}
    @Override public void removeDeviceListener(IInputDeviceListener l){listeners.remove(l);}

    /* ─── ControllerListener ───────────────────────── */
    // FIXME these should use interfaces
    @Override
    public void connected(Controller c) {
        IControllerAdapter adapter = factory.createController(c);
        adapter.setController(c);
        map.put(c, adapter);
        listeners.forEach(l -> l.onAdapterConnected(adapter));
    }

    @Override
    public void disconnected(Controller c) {
        IInputAdapter adapter = map.remove(c);
        if (adapter != null) {
            listeners.forEach(l -> l.onAdapterDisconnected(adapter));
        }
    }

    // remaining callbacks → no-ops
    @Override public boolean buttonDown(Controller c,int b){return false;}
    @Override public boolean buttonUp(Controller c,int b){return false;}
    @Override public boolean axisMoved(Controller c,int a,float v){return false;}
}
