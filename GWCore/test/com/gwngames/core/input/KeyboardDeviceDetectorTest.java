package com.gwngames.core.input;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.input.adapter.KeyboardInputAdapter;
import com.gwngames.core.input.detector.PeripheralDeviceDetector;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ensures that PeripheralDeviceDetector picks up a single hardware keyboard.
 */
public class KeyboardDeviceDetectorTest extends BaseTest {

    /* ───────────────────────── dummy Gdx.input ───────────────────────── */

    /**
     * A minimal stub that tells LibGDX a hardware keyboard is present.
     * All other methods are unsupported – they need not run in this unit test.
     */

    /* ───────────────────────── BaseTest entry-point ──────────────────── */
    @Override
    protected void runTest() {
        /* install dummy LibGDX singletons */
        setupApplication();

        /*  create detector + listener hooks */
        PeripheralDeviceDetector detector = new PeripheralDeviceDetector();

        AtomicInteger connectCnt = new AtomicInteger();
        AtomicReference<IInputAdapter> captured = new AtomicReference<>();

        detector.addDeviceListener(new IInputDeviceListener() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override
            public void onAdapterConnected(IInputAdapter adapter) {
                connectCnt.incrementAndGet();
                captured.set(adapter);
            }
            @Override
            public void onAdapterDisconnected(IInputAdapter adapter) { /* not needed */ }
        });

        /*  start detector (runs initial scan immediately) */
        Cdi.inject(detector);
        detector.start();

        /*  assertions */
        Assertions.assertEquals(1, connectCnt.get(), "Exactly one keyboard should be detected");
        Assertions.assertNotNull(captured.get(), "Adapter instance expected");
        Assertions.assertInstanceOf(KeyboardInputAdapter.class, captured.get(), "Detected adapter must be a KeyboardInputAdapter");
    }
}
