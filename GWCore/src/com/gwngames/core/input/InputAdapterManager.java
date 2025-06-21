package com.gwngames.core.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;

import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.input.detector.ControllerDeviceDetector;
import com.gwngames.core.input.detector.PeripheralDeviceDetector;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Init(module = ModuleNames.CORE)
public class InputAdapterManager extends BaseComponent implements IInputAdapterManager, IInputDeviceListener{
    /* ───────────────────────── constants & singleton ───────────────────────── */
    public static final int MAX_SLOTS = 4; // todo: to config
    private static final InputAdapterManager INSTANCE = new InputAdapterManager();

    public static InputAdapterManager get() { return INSTANCE; }

    /* ───────────────────────── state ───────────────────────── */

    /** slot-assigned adapters (index = slot) */
    private final IInputAdapter[] slots = new IInputAdapter[MAX_SLOTS];

    /** every adapter detected in the running app */
    private final List<IInputAdapter> allAdapters    = new CopyOnWriteArrayList<>();
    /** adapters currently occupying slots */
    private final List<IInputAdapter> activeAdapters = new CopyOnWriteArrayList<>();

    /** app-level listeners that want plug/unplug callbacks */
    private final List<IInputDeviceListener> deviceListeners = new CopyOnWriteArrayList<>();

    /** registered detectors (you can add/remove more at runtime) */
    private final List<IDeviceDetector> detectors = new CopyOnWriteArrayList<>();

    /** –1 ⇒ no main adapter chosen yet */
    private int mainSlot = -1;

    /* ───────────────────────── ctor ───────────────────────── */

    private InputAdapterManager() {
        /* Register two out-of-the-box detectors:
           – gamepad hot-plug via LibGDX Controllers
           – keyboard / multitouch peripherals                               */
        addDetector(new ControllerDeviceDetector());
        addDetector(new PeripheralDeviceDetector());   // polls keyboard/touch availability
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
