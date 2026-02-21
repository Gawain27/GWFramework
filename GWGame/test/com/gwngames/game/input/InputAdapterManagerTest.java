package com.gwngames.game.input;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.util.Cdi;
import com.gwngames.game.api.input.*;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies slot logic + main-controller promotion AND correct
 * connect / disconnect listener counts via a fake detector,
 * using the framework for the manager lifecycle/injection.
 */
public class InputAdapterManagerTest extends BaseTest {

    /** Get the real framework-managed manager (not `new`). */
    @Inject
    private IInputAdapterManager mgr; // if you have an interface, prefer that: IInputAdapterManager

    /* ─── simple stub adapter ─────────────────────────────────────────── */
    private static final class StubAdapter implements IInputAdapter {
        private final String name;
        private int slot = -1;
        private boolean started;

        StubAdapter(String n) { name = n; }

        @Override public void start() { started = true; }
        @Override public void stop()  { started = false; }

        @Override public void addListener(IInputListener l) {}
        @Override public void removeListener(IInputListener l) {}
        @Override public List<IInputListener> getListeners() { return List.of(); }

        @Override public String getAdapterName() { return name; }
        @Override public int getSlot() { return slot; }
        @Override public void setSlot(int s) { slot = s; }

        boolean isStarted() { return started; }
    }

    /* ─── fake detector that we manually trigger ──────────────────────── */
    private static final class FakeDetector implements IDeviceDetector {
        private IInputDeviceListener listener;

        @Override public void start() {}
        @Override public void stop() {}

        @Override public void addDeviceListener(IInputDeviceListener l) { listener = l; }
        @Override public void removeDeviceListener(IInputDeviceListener l) { listener = null; }

        void fireConnect(IInputAdapter a) {
            if (listener != null) listener.onAdapterConnected(a);
        }

        void fireDisconnect(IInputAdapter a) {
            if (listener != null) listener.onAdapterDisconnected(a);
        }
    }

    /* Framework-friendly device listener (has multId, can be cached, etc.) */
    private static final class CountingDeviceListener extends BaseComponent implements IInputDeviceListener {
        private final AtomicInteger connects;
        private final AtomicInteger disconnects;

        CountingDeviceListener(AtomicInteger c, AtomicInteger d) {
            this.connects = c;
            this.disconnects = d;
        }

        @Override public void onAdapterConnected(IInputAdapter a) { connects.incrementAndGet(); }
        @Override public void onAdapterDisconnected(IInputAdapter a) { disconnects.incrementAndGet(); }
    }

    @Override
    protected void runTest() {
        setupApplication(); // your BaseTest already CDI-injects the test instance in setup

        // Safety: if BaseTest doesn't inject the test instance in some cases, this makes it explicit.
        // (If it *already* does, this is harmless because your CDI is idempotent.)
        Cdi.inject(this);

        Assertions.assertNotNull(mgr, "InputAdapterManager should be injected by the framework");

        /* clear any residual slots from prior tests */
        mgr.getActiveAdapters().forEach(a -> mgr.unregister(a.getSlot()));

        /* hook listener counters */
        AtomicInteger connects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        mgr.addDeviceListener(new CountingDeviceListener(connects, disconnects));

        /* install fake detector */
        FakeDetector det = new FakeDetector();
        mgr.addDetector(det);

        /* simulate hardware plug */
        StubAdapter pad0 = new StubAdapter("Pad-0");
        det.fireConnect(pad0);
        Assertions.assertEquals(1, connects.get(), "Connect listener fired once");

        /* register pad0 into slot 2 → becomes main */
        mgr.register(2, pad0);
        Assertions.assertSame(pad0, mgr.getMainAdapter(), "Pad-0 should be promoted as main");

        /* plug second device */
        StubAdapter pad1 = new StubAdapter("Pad-1");
        det.fireConnect(pad1);
        Assertions.assertEquals(2, connects.get(), "Second connect listener fired");

        mgr.register(1, pad1);

        /* remove main adapter via unregister() – should promote pad1 */
        mgr.unregister(2);
        Assertions.assertEquals(0, disconnects.get(),
            "Disconnect should not fire on logical un-slot");

        // now simulate hardware unplug of pad1 → now fires
        det.fireDisconnect(pad1);
        Assertions.assertEquals(1, disconnects.get(),
            "Disconnect listener fired once after physical unplug");
    }
}
