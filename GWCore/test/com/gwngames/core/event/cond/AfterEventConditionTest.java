package com.gwngames.core.event.cond;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.queue.MasterEventQueue;
import org.junit.jupiter.api.Assertions;

public class AfterEventConditionTest extends BaseTest {

    @Override
    protected void runTest() {
        setupApplication();

        MasterEventQueue master = new MasterEventQueue();
        MacroEvent macro = new MacroEvent(); //TODO use event manager
        macro.setId("macro-X");

        SimpleEvent prerequisite = new SimpleEvent();
        SimpleEvent candidate    = new SimpleEvent();
        macro.addEvent(prerequisite);
        macro.addEvent(candidate);

        AfterEventCondition cond = new AfterEventCondition();
        cond.setPrerequisite(prerequisite);

        /* 1 — prerequisite not executed → result is WAIT                */
        Assertions.assertSame(ConditionResult.WAIT,
            cond.evaluate(candidate, master),
            "prerequisite not executed");

        /* 2 — prerequisite executed → result becomes TRUE               */
        master.markExecuted(prerequisite);
        Assertions.assertSame(ConditionResult.TRUE,
            cond.evaluate(candidate, master),
            "prerequisite executed");
    }
}
