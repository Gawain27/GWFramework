package com.gwngames.core.event.trigger;

import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.queue.MasterEventQueue;
import com.gwngames.core.event.queue.ConcurrentSubQueue;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ensures that ManualTrigger enqueues its payload exactly
 * once when {@link ManualTrigger#fire()} is called.
 */
public class ManualTriggerTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        MasterEventQueue master = new MasterEventQueue();
        CountDownLatch   done   = new CountDownLatch(1);

        /* lightweight sub-queue that just counts down the latch */
        ConcurrentSubQueue<SimpleEvent> subQ = new ConcurrentSubQueue<>(1) {
            @Override protected void processEvent(SimpleEvent e) throws EventException {
                done.countDown();
            }
        };
        master.registerSubQueue(SimpleEvent.class, subQ);

        /* ---- trigger + payload ---------------------------------------- */
        ManualTrigger trigger = new ManualTrigger();
        trigger.setSinglePayload(new SimpleEvent());
        master.registerTrigger(trigger);

        /* ---- act ------------------------------------------------------- */
        trigger.fire();                 // request firing on next poll
        master.process(0f);             // one engine tick

        /* ---- assert ---------------------------------------------------- */
        boolean finished = done.await(200, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(finished, "ManualTrigger should have executed its event");

        subQ.shutdown();
    }
}
