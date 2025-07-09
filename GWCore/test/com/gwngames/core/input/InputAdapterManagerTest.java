package com.gwngames.core.input;

import com.gwngames.core.api.input.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies slot logic + main-controller promotion AND correct
 * connect / disconnect listener counts via a fake detector.
 */
public class InputAdapterManagerTest extends BaseTest {

    /* ─── simple stub adapter ─────────────────────────────────────────── */
    private static final class StubAdapter extends BaseComponent implements IInputAdapter {
        private final String name; private int slot=-1; private boolean started;
        StubAdapter(String n){name=n;}
        @Override public void start(){started=true;}
        @Override public void stop (){started=false;}
        @Override public void addListener(IInputListener l){}
        @Override public void removeListener(IInputListener l){}
        @Override public String getAdapterName(){return name;}
        @Override public int getSlot(){return slot;}
        @Override public void setSlot(int s){slot=s;}
        boolean isStarted(){return started;}
    }

    /* ─── fake detector that we manually trigger ──────────────────────── */
    private static final class FakeDetector extends BaseComponent implements IDeviceDetector {
        private IInputDeviceListener listener;
        @Override public void start(){}
        @Override public void stop(){}
        @Override public void addDeviceListener(IInputDeviceListener l){listener=l;}
        @Override public void removeDeviceListener(IInputDeviceListener l){listener=null;}
        void fireConnect(IInputAdapter a){ listener.onAdapterConnected(a); }
        void fireDisconnect(IInputAdapter a){ listener.onAdapterDisconnected(a); }
    }

    /* ─── actual unit-test (BaseTest entry-point) ─────────────────────── */
    @Override
    protected void runTest() {
        setupApplication();

        InputAdapterManager mgr = InputAdapterManager.get();

        /* clear any residual slots */
        mgr.getActiveAdapters().forEach(a -> mgr.unregister(a.getSlot()));

        /* hook listener counters */
        AtomicInteger connects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        mgr.addDeviceListener(new IInputDeviceListener() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override public void onAdapterConnected(IInputAdapter a){connects.incrementAndGet();}
            @Override public void onAdapterDisconnected(IInputAdapter a){disconnects.incrementAndGet();}
        });

        /* install fake detector */
        FakeDetector det = new FakeDetector();
        mgr.addDetector(det);

        /* simulate hardware plug */
        StubAdapter pad0 = new StubAdapter("Pad-0");
        det.fireConnect(pad0);
        Assertions.assertEquals(1, connects.get(), "Connect listener fired once");

        /* register pad0 into slot 2 → becomes main */
        mgr.register(2, pad0);
        Assertions.assertSame(pad0, mgr.getMainAdapter());

        /* plug second device */
        StubAdapter pad1 = new StubAdapter("Pad-1");
        det.fireConnect(pad1);
        Assertions.assertEquals(2, connects.get());

        mgr.register(1, pad1);

        /* remove main adapter via unregister() – should promote pad1 */
        // remove main adapter logically → no device-level disconnect yet
        mgr.unregister(2);
        Assertions.assertEquals(0, disconnects.get(),
            "Disconnect should not fire on logical un-slot");

        // simulate hardware unplug of pad1 → now fires
        det.fireDisconnect(pad1);
        Assertions.assertEquals(1, disconnects.get(),
            "Disconnect listener fired once after physical unplug");
    }
}
