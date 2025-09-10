package com.gwngames.core.event.trigger;

import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.queue.MasterEventQueue;
import com.gwngames.core.event.queue.ConcurrentSubQueue;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ensures that a TimeTrigger with delay 0 fires immediately
 * (on the very first call to {@link MasterEventQueue#process(float)}).
 */
public class TimeTriggerTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        MasterEventQueue master = new MasterEventQueue();
        CountDownLatch   done   = new CountDownLatch(1);

        ConcurrentSubQueue<SimpleEvent> subQ = new ConcurrentSubQueue<>() {
            @Override protected void processEvent(SimpleEvent e) throws EventException {
                done.countDown();
            }

            @Override
            public Class<SimpleEvent> getType() {
                return SimpleEvent.class;
            }
        };

        /* ---- trigger: fire immediately, one-shot ---------------------- */
        TimeTrigger trigger = new TimeTrigger();
        master.registerTrigger(trigger);

        /* ---- act ------------------------------------------------------- */
        master.process(0f);             // first tick — should fire

        /* ---- assert ---------------------------------------------------- */
        boolean finished = done.await(200, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(finished, "TimeTrigger should have executed its event");

        subQ.shutdown();
    }
}
