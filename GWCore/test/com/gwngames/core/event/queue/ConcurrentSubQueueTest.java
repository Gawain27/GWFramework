package com.gwngames.core.event.queue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.EventStatus;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.DummyEventException;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.event.base.MacroEvent;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit-tests for {@link ConcurrentSubQueue}.
 */
public final class ConcurrentSubQueueTest extends BaseTest {

    private static final ITranslatable DUMMY = new ITranslatable() {
        @Override public String getKey()            { return "dummy"; }
        @Override public String getDefaultCaption() { return "dummy-caption"; }
    };

    /** Sub-queue that records the peak number of concurrently running events. */
    private static final class CountingSubQueue extends ConcurrentSubQueue<SimpleEvent> {
        private final CountDownLatch doneLatch;
        private final AtomicInteger  running     = new AtomicInteger(0);
        private final AtomicInteger  peakRunning = new AtomicInteger(0);

        CountingSubQueue(int maxParallel, CountDownLatch doneLatch) {
            super(maxParallel);
            this.doneLatch = doneLatch;
        }
        @Override protected void processEvent(SimpleEvent evt) {
            int inFlight = running.incrementAndGet();
            peakRunning.updateAndGet(p -> Math.max(p, inFlight));
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
            running.decrementAndGet();
            doneLatch.countDown();
        }
        int peak() { return peakRunning.get(); }
    }

    /** Sub-queue that always throws an {@link EventException}. */
    private static final class FailingSubQueue extends ConcurrentSubQueue<SimpleEvent> {
        FailingSubQueue() { super(1); }
        @Override protected void processEvent(SimpleEvent evt) throws EventException {
            throw new DummyEventException(DUMMY);
        }
    }

    /** Master that flips a latch when it sees an EventException. */
    private static final class CapturingMaster extends MasterEventQueue {
        private final CountDownLatch latch;
        CapturingMaster(CountDownLatch latch) { this.latch = latch; }
        @Override
        public void handleEventException(com.gwngames.core.api.event.IEvent evt, EventException ex) {
            super.handleEventException(evt, ex);
            latch.countDown();
        }
    }

    /* ───────────────────────────── test body ────────────────────────── */

    @Override
    protected void runTest() throws Exception {
        //testConcurrencyLimit();
        //testExceptionPropagation();
    }

    /* ---------- 1) verify max-parallel behaviour ---------- */

    private void testConcurrencyLimit() throws Exception {
        final int MAX_PARALLEL = 2;
        final int TOTAL_EVENTS = 6;

        CountDownLatch done = new CountDownLatch(TOTAL_EVENTS);
        CountingSubQueue subQueue = new CountingSubQueue(MAX_PARALLEL, done);

        MasterEventQueue master = new MasterEventQueue();
        master.registerSubQueue(SimpleEvent.class, subQueue);

        MacroEvent macro = new MacroEvent("macro-ok");
        for (int i = 0; i < TOTAL_EVENTS; i++) macro.addEvent(new SimpleEvent());
        master.enqueueMacroEvent(macro);

        /* drive the queue until all events finished (≤ 3 s) */
        assertTimeout(3_000, () -> {
            while (done.getCount() > 0) {
                master.process(0f);
                Thread.sleep(10);
            }
        });

        /* assertions */
        Assertions.assertEquals(MAX_PARALLEL, subQueue.peak(),
            "No more than " + MAX_PARALLEL + " events ran in parallel");
        macro.getEvents().forEach(e -> Assertions.assertEquals(
            EventStatus.COMPLETED, ((AbstractEvent) e).getStatus()));
        subQueue.shutdown();
    }

    /* ---------- 2) verify EventException is captured ---------- */
    private void testExceptionPropagation() throws Exception {
        CountDownLatch caught = new CountDownLatch(1);

        CapturingMaster master = new CapturingMaster(caught);
        master.registerSubQueue(SimpleEvent.class, new FailingSubQueue());

        MacroEvent macro = new MacroEvent("macro-fail");
        macro.addEvent(new SimpleEvent());
        master.enqueueMacroEvent(macro);

        /* submit work once, then await callback */
        master.process(0f);

        boolean signalled = caught.await(2, TimeUnit.SECONDS);   // <= give the thread ample time
        Assertions.assertTrue(signalled,
            "EventException should propagate to MasterEventQueue within the timeout");
    }
}
