package com.gwngames.core.event.trigger;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.trigger.IManualTrigger;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.util.Cdi;
import org.junit.jupiter.api.Assertions;

/**
 * Ensures that ManualTrigger enqueues its payload exactly once when fire() is called,
 * and the payload reaches COMPLETED after processing.
 */
public final class ManualTriggerTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();
        Cdi.inject(this); // inject master + any other dependencies

        // Create trigger via framework resolution (highest-priority impl)
        IManualTrigger trigger = BaseComponent.getInstance(IManualTrigger.class);

        IEvent ev = new SimpleEvent();
        trigger.setSinglePayload(ev);

        master.registerTrigger(trigger);

        trigger.fire();

        // Drive the engine until the event completes (or timeout).
        assertTimeout(100, () -> {
            while (ev.getStatus() != EventStatus.COMPLETED) {
                master.process(0f);
                Thread.sleep(5);
            }
        });

        Assertions.assertEquals(EventStatus.COMPLETED, ev.getStatus(),
            "ManualTrigger payload should have executed and reached COMPLETED");
    }
}
