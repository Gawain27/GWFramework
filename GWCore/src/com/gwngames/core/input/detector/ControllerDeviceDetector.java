package com.gwngames.core.input.detector;

import com.badlogic.gdx.controllers.*;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.input.adapter.ControllerInputAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Init(module = ModuleNames.CORE, subComp = SubComponentNames.CONTROLLER_DETECTOR)
public class ControllerDeviceDetector implements IDeviceDetector, ControllerListener {

    private final Map<Controller, IInputAdapter> map = new ConcurrentHashMap<>();
    private final List<IInputDeviceListener> listeners = new CopyOnWriteArrayList<>();

    @Override public void start() { Controllers.addListener(this); }

    @Override public void stop()  { Controllers.removeListener(this); }

    /* ─── IDeviceDetector ───────────────────────────── */

    @Override public void addDeviceListener(IInputDeviceListener l){listeners.add(l);}
    @Override public void removeDeviceListener(IInputDeviceListener l){listeners.remove(l);}

    /* ─── ControllerListener ───────────────────────── */

    @Override
    public void connected(Controller c) {
        IInputAdapter adapter = new ControllerInputAdapter(c);
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
