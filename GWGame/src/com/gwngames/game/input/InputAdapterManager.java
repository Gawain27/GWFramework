package com.gwngames.game.input;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;

import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.data.input.InputParameters;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.IDeviceDetector;
import com.gwngames.game.api.input.IInputAdapter;
import com.gwngames.game.api.input.IInputAdapterManager;
import com.gwngames.game.api.input.IInputDeviceListener;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Init(module = GameModule.GAME)
public class InputAdapterManager extends BaseComponent implements IInputAdapterManager, IInputDeviceListener{
    @Inject
    private IConfig config;

    @Inject(loadAll = true)
    private List<IDeviceDetector> injectedDetectors;

    private int MAX_SLOTS;
    private IInputAdapter[] slots;

    private final List<IInputAdapter> allAdapters    = new CopyOnWriteArrayList<>();
    private final List<IInputAdapter> activeAdapters = new CopyOnWriteArrayList<>();
    private final List<IInputDeviceListener> deviceListeners = new CopyOnWriteArrayList<>();

    /** Runtime mutable list (seeded from injectedDetectors). */
    private final List<IDeviceDetector> detectors = new CopyOnWriteArrayList<>();

    private int mainSlot = -1;

    @PostInject
    void init() {
        this.MAX_SLOTS = config.get(InputParameters.INPUT_MAX_DEVICES);
        this.slots = new IInputAdapter[MAX_SLOTS];

        // Seed runtime list from framework list and start them
        if (injectedDetectors != null) {
            for (IDeviceDetector d : injectedDetectors) {
                addDetector(d);
            }
        }
    }

    @Override
    public void addDetector(IDeviceDetector detector) {
        if (detector == null) return;
        detectors.add(detector);
        detector.addDeviceListener(this);
        detector.start();
    }

    public void removeDetector(IDeviceDetector detector) {
        if (detector == null) return;
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
