package com.gwngames.core.event.queue;

import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.DummyEventException;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.event.base.MacroEvent;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;

/**
 * Unit tests for {@link MasterEventQueue}.
 */
// TODO: make tests adhere to class loading logic
public final class MasterEventQueueTest extends BaseTest {


    /** Dummy translation key used when throwing EventException. */
    private static final ITranslatable DUMMY_TRANSLATABLE = new ITranslatable() {
        @Override public String getKey()            { return "dummy.key"; }
        @Override public String getDefaultCaption() { return "Dummy caption"; }
    };

    /**
     * Sub-queue that just completes events successfully and counts them
     * down via the provided latch.
     */
    private static final class SimpleSubQueue extends ConcurrentSubQueue<SimpleEvent> {
        private final CountDownLatch latch;
        SimpleSubQueue(int maxParallel, CountDownLatch latch, MasterEventQueue master) {
            super();
            this.latch = latch;
        }
        @Override protected void processEvent(SimpleEvent event) {
            latch.countDown(); // mark success for test synchronisation
        }

        @Override
        public Class<SimpleEvent> getType() {
            return SimpleEvent.class;
        }
    }

    /**
     * Sub-queue that *always* throws {@link EventException}.
     * A latch is triggered via the post-exception action in
     * {@link MasterEventQueue} – not here.
     */
    private static final class FaultySubQueue extends ConcurrentSubQueue<SimpleEvent> {
        FaultySubQueue(MasterEventQueue master) {
            super();
        }
        @Override
        protected void processEvent(SimpleEvent event) throws EventException {
            throw new DummyEventException(DUMMY_TRANSLATABLE);
        }

        @Override
        public Class<SimpleEvent> getType() {
            return SimpleEvent.class;
        }
    }

    /* ────────────────────────────── tests ─────────────────────────────── */

    @Override
    protected void runTest() throws Exception {

        /* ---------- 1) happy-path processing ---------- */
        CountDownLatch successLatch = new CountDownLatch(2);

        MasterEventQueue master = new MasterEventQueue();
        SimpleSubQueue   okQueue = new SimpleSubQueue(2, successLatch, master);

        MacroEvent macro = new MacroEvent();
        macro.setId("macro-1");
        SimpleEvent e1 = new SimpleEvent();
        SimpleEvent e2 = new SimpleEvent();
        macro.addEvent(e1);
        macro.addEvent(e2);

        master.enqueueMacroEvent(macro);

        // Drive the queue until both events finish (max 1 s).
        assertTimeout(1_000, () -> {
            while (successLatch.getCount() > 0) {
                master.process(0f);
                Thread.sleep(10);
            }
        });

        Assertions.assertTrue(master.hasExecuted(e1), "Event 1 executed");
        Assertions.assertTrue(master.hasExecuted(e2), "Event 2 executed");
        Assertions.assertTrue(master.isMacroEventCompleted(macro), "Macro completed");

        okQueue.shutdown();   // tidy up threads



        /* ---------- 2) exception handling path ---------- */
        CountDownLatch exceptionLatch = new CountDownLatch(1);

        MasterEventQueue faultyMaster = new MasterEventQueue();

        // Notify the test when the exception bubbles up.
        faultyMaster.setPostExceptionAction((evt, ex) -> exceptionLatch.countDown());

        MacroEvent faultyMacro = new MacroEvent();
        faultyMacro.setId("macro-error");
        faultyMacro.addEvent(new SimpleEvent());
        faultyMaster.enqueueMacroEvent(faultyMacro);

        // Run until the post-exception action fires (max 2 s, thread pool takes 1 s to setup).
        assertTimeout(2_000, () -> {
            while (exceptionLatch.getCount() > 0) {
                faultyMaster.process(0f);
                Thread.sleep(10);
            }
        });

        Assertions.assertEquals(0, exceptionLatch.getCount(),
            "EventException was caught and post-action executed");
    }
}
