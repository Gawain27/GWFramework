package com.gwngames.core.input.detector;

import com.badlogic.gdx.Input.Peripheral;
import com.badlogic.gdx.Gdx;

import com.gwngames.core.api.input.*;
import com.gwngames.core.input.adapter.KeyboardInputAdapter;
import com.gwngames.core.input.adapter.TouchInputAdapter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class PeripheralDeviceDetector implements IDeviceDetector {

    private static final float RECHECK_SECONDS = 1.5f;   // set < 0 to disable polling TODO: to config

    private final List<IInputDeviceListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Peripheral, IInputAdapter> created = new EnumMap<>(Peripheral.class);

    private float elapsed = 0f;  // for polling

    @Override public void start() { initialScan(); }

    @Override public void stop()  { } // nothing to tear down

    @Override public void addDeviceListener(IInputDeviceListener l){listeners.add(l);}
    @Override public void removeDeviceListener(IInputDeviceListener l){listeners.remove(l);}

    /** call from your game’s render() if polling enabled */
    public void tick(float delta) {
        if (RECHECK_SECONDS <= 0) return;
        elapsed += delta;
        if (elapsed >= RECHECK_SECONDS) {
            elapsed = 0f;
            rescan();
        }
    }

    /* ─── internal ─────────────────────────────────── */

    private void initialScan() {
        rescan();  // keyboard / touch present at launch
    }

    private void rescan() {
        checkPeripheral(Peripheral.HardwareKeyboard, KeyboardInputAdapter::new);
        checkPeripheral(Peripheral.MultitouchScreen, TouchInputAdapter::new);
    }

    private void checkPeripheral(Peripheral p, Supplier<IInputAdapter> factory) {
        boolean available = Gdx.input.isPeripheralAvailable(p);
        boolean already   = created.containsKey(p);

        if (available && !already) {           // newly present
            IInputAdapter adapter = factory.get();
            created.put(p, adapter);
            listeners.forEach(l -> l.onAdapterConnected(adapter));

        } else if (!available && already) {    // removed
            IInputAdapter adapter = created.remove(p);
            listeners.forEach(l -> l.onAdapterDisconnected(adapter));
        }
    }
}
