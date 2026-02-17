package com.gwngames.game.input.detector;

import com.badlogic.gdx.Input.Peripheral;
import com.badlogic.gdx.Gdx;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.data.input.InputParameters;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.input.IDeviceDetector;
import com.gwngames.game.api.input.IInputAdapter;
import com.gwngames.game.api.input.IInputAdapterFactory;
import com.gwngames.game.api.input.IInputDeviceListener;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Init(module = GameModule.GAME, subComp = GameSubComponent.PERIPHERAL_DETECTOR)
public class PeripheralDeviceDetector extends BaseComponent implements IDeviceDetector {
    @Inject
    private IConfig config;
    @Inject
    private IInputAdapterFactory factory;

    // set < 0 to disable polling
    private float RECHECK_SECONDS;

    @PostInject
    void init(){
        RECHECK_SECONDS = config.get(InputParameters.INPUT_DEVICE_POLLING);
    }

    private final List<IInputDeviceListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Peripheral, IInputAdapter> created = new EnumMap<>(Peripheral.class);

    private float elapsed = 0f;  // for polling

    @Override public void start() { initialScan(); }

    @Override public void stop()  { } // nothing to tear down

    @Override
    public void addDeviceListener(IInputDeviceListener l){
        listeners.add(l);
    }
    @Override
    public void removeDeviceListener(IInputDeviceListener l){
        listeners.remove(l);
    }

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
        checkPeripheral(Peripheral.HardwareKeyboard, () -> factory.createKeyboard());
        checkPeripheral(Peripheral.MultitouchScreen, () -> factory.createTouch());
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
