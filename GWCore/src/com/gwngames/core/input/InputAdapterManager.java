package com.gwngames.core.input;

import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.input.*;

import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.input.InputParameters;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Init(module = ModuleNames.CORE)
public class InputAdapterManager extends BaseComponent implements IInputAdapterManager, IInputDeviceListener{
    @Inject
    private IClassLoader loader;
    @Inject
    private IConfig config;

    private int MAX_SLOTS;
    /** slot-assigned adapters (index = slot) */
    private IInputAdapter[] slots;

    /** every adapter detected in the running app */
    private final List<IInputAdapter> allAdapters = new CopyOnWriteArrayList<>();
    /** adapters currently occupying slots */
    private final List<IInputAdapter> activeAdapters = new CopyOnWriteArrayList<>();

    /** app-level listeners that want plug/unplug callbacks */
    private final List<IInputDeviceListener> deviceListeners = new CopyOnWriteArrayList<>();

    /** registered detectors (you can add/remove more at runtime) */
    private final List<IDeviceDetector> detectors = new CopyOnWriteArrayList<>();

    /** –1 ⇒ no main adapter chosen yet */
    private int mainSlot = -1;

    /* ───────────────────────── ctor ───────────────────────── */

    public InputAdapterManager() {
        /* Register two out-of-the-box detectors:
           – gamepad hot-plug via LibGDX Controllers
           – keyboard / multitouch peripherals */
        IDeviceDetector controllerDeviceDetector = loader.tryCreate(ComponentNames.DEVICE_DETECTOR, SubComponentNames.CONTROLLER_DETECTOR);
        IDeviceDetector perifDeviceDetector = loader.tryCreate(ComponentNames.DEVICE_DETECTOR, SubComponentNames.PERIPHERAL_DETECTOR);

        addDetector(controllerDeviceDetector);
        addDetector(perifDeviceDetector);   // polls keyboard/touch availability
    }

    @PostInject
    void init() {
        this.MAX_SLOTS = config.get(InputParameters.INPUT_MAX_DEVICES);
        slots = new IInputAdapter[MAX_SLOTS];
    }

    /* ───────────────────────── detector management ───────────────────────── */

    /** Plug in another detector (e.g. a VR-handset detector) */
    public void addDetector(IDeviceDetector detector) {
        detectors.add(detector);
        detector.addDeviceListener(this);   // we receive its callbacks
        detector.start();
    }

    public void removeDetector(IDeviceDetector detector) {
        detector.stop();
        detector.removeDeviceListener(this);
        detectors.remove(detector);
    }

    /* ───────────────────────── IInputAdapterManager ───────────────────────── */

    @Override
    public synchronized void register(int slot, IInputAdapter adapter) {
        if (slot < 0 || slot >= MAX_SLOTS)
            throw new IllegalArgumentException("Slot out of range: " + slot);

        unregister(slot);                 // clear anything already there
        slots[slot] = adapter;
        adapter.setSlot(slot);
        adapter.start();
        activeAdapters.add(adapter);

        /* auto-promote to main if none yet */
        if (mainSlot == -1) {
            mainSlot = slot;
        }
    }

    @Override
    public synchronized void unregister(int slot) {
        IInputAdapter adapter = slots[slot];
        if (adapter == null) return;

        adapter.stop();
        adapter.setSlot(-1);
        activeAdapters.remove(adapter);
        slots[slot] = null;

        /* if we removed the main adapter, choose a new one */
        if (slot == mainSlot) {
            mainSlot = -1;
            promoteFirstAvailableAsMain();
        }
    }

    @Override
    public synchronized void setMainController(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS || slots[slot] == null)
            throw new IllegalStateException("Invalid main controller slot: " + slot);
        mainSlot = slot;
    }

    @Override public IInputAdapter getAdapter(int slot) { return slots[slot]; }

    @Override public IInputAdapter getMainAdapter() { return (mainSlot >= 0) ? slots[mainSlot] : null; }

    /* ───────────────────────── collections & listeners ───────────────────── */

    @Override public List<IInputAdapter> getAllAdapters()    { return Collections.unmodifiableList(allAdapters); }
    @Override public List<IInputAdapter> getActiveAdapters() { return Collections.unmodifiableList(activeAdapters); }

    @Override public void addDeviceListener(IInputDeviceListener l)   { deviceListeners.add(l); }
    @Override public void removeDeviceListener(IInputDeviceListener l){ deviceListeners.remove(l); }

    /* ───────────────────────── detector callbacks ───────────────────────── */

    @Override
    public synchronized void onAdapterConnected(IInputAdapter adapter) {
        allAdapters.add(adapter);
        //FIXME for now we do autoconnect, but we'll have to improve this
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] == null) {
                register(i, adapter);
                break;
            }
        }
        deviceListeners.forEach(l -> l.onAdapterConnected(adapter));
    }

    @Override
    public synchronized void onAdapterDisconnected(IInputAdapter adapter) {
        /* if it occupied a slot, free it (which will also handle main promotion) */
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] == adapter) {
                unregister(i);
                break;
            }
        }
        allAdapters.remove(adapter);
        deviceListeners.forEach(l -> l.onAdapterDisconnected(adapter));
    }

    /* ───────────────────────── helpers ───────────────────────── */

    /** pick the first occupied slot as the new main controller */
    private void promoteFirstAvailableAsMain() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] != null) {
                mainSlot = i;
                return;
            }
        }
        mainSlot = -1;   // none left
    }
}
