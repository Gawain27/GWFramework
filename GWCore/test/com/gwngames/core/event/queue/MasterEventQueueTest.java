package com.gwngames.core.event.queue;

import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.event.base.MacroEvent;
import org.junit.jupiter.api.Assertions;

public final class MasterEventQueueTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        IMasterEventQueue masterIface = BaseComponent.getInstance(IMasterEventQueue.class);
        Assertions.assertNotNull(masterIface);

        MasterEventQueue master = (MasterEventQueue) masterIface;

        MacroEvent macro = new MacroEvent();
        macro.setId("macro-system-1");

        SimpleEvent ev = new SimpleEvent();
        macro.addEvent(ev);

        master.enqueueMacroEvent(macro);

        // Drive until the event completes (max 2s)
        assertTimeout(2_000, () -> {
            while (ev.getStatus() != EventStatus.COMPLETED) {
                master.process(0f);
                Thread.sleep(5);
            }
        });

        Assertions.assertTrue(master.hasExecuted(ev), "Event should be marked executed by master");
        Assertions.assertTrue(master.isMacroEventCompleted(macro), "Macro should be completed");
    }
}
