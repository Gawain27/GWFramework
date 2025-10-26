package com.gwngames.core.event.trigger;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.api.event.trigger.IManualTrigger;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.queue.ConcurrentSubQueue;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ensures that ManualTrigger enqueues its payload exactly
 * once when {@link ManualTrigger#fire()} is called.
 */
public class ManualTriggerTest extends BaseTest {
    @Inject
    private IMasterEventQueue master;

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        CountDownLatch done = new CountDownLatch(1);

        /* lightweight sub-queue that just counts down the latch */
        ConcurrentSubQueue<SimpleEvent> subQ = new ConcurrentSubQueue<>() {
            @Override
            protected void processEvent(SimpleEvent e) {
                done.countDown();
            }

            @Override
            public Class<SimpleEvent> getType() {
                return SimpleEvent.class;
            }
        };
        Cdi.inject(subQ);
        Method m = ConcurrentSubQueue.class.getDeclaredMethod("init");
        m.setAccessible(true);
        Field f = ConcurrentSubQueue.class.getDeclaredField("maxParallel");
        f.setAccessible(true);
        f.set(subQ, 2);
        m.invoke(subQ);

        /* ---- trigger + payload ---------------------------------------- */
        IManualTrigger trigger = BaseComponent.getInstance(IManualTrigger.class);
        trigger.setSinglePayload(new SimpleEvent());
        master.registerTrigger(trigger);
        master.registerQueue(subQ);

        /* ---- act ------------------------------------------------------- */
        trigger.fire();                 // request firing on next poll
        master.process(0f);             // one engine tick

        /* ---- assert ---------------------------------------------------- */
        boolean finished = done.await(200, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(finished, "ManualTrigger should have executed its event");

        subQ.shutdown();
    }
}
