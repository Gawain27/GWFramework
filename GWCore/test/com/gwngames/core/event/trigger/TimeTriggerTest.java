package com.gwngames.core.event.trigger;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.event.queue.MasterEventQueue;
import com.gwngames.core.util.Cdi;
import com.gwngames.core.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;

/**
 * Ensures that a TimeTrigger with delay 0 fires immediately
 * (on the very first call to {@link MasterEventQueue#process(float)}).
 */
public class TimeTriggerTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        /* ---- trigger: fire immediately, one-shot ---------------------- */
        TimeTrigger trigger = new TimeTrigger();
        trigger.setNextFireAt(System.currentTimeMillis() / 2);
        SimpleEvent triggerEvent = new SimpleEvent();
        trigger.setSinglePayload(triggerEvent);
        ReflectionUtils.setField(trigger, "id", "trigger-test");
        master.registerTrigger(trigger);
        Cdi.inject(trigger);

        /* ---- act ------------------------------------------------------- */
        master.process(0f);             // first tick — should fire
        Thread.sleep(50);
        /* ---- assert ---------------------------------------------------- */
        Assertions.assertEquals(EventStatus.COMPLETED, triggerEvent.getStatus(), "TimeTrigger should have executed its event");
    }
}
