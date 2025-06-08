package com.gwngames.core.event.cond;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.queue.MasterEventQueue;
import org.junit.jupiter.api.Assertions;

public class AfterMacroEventConditionTest extends BaseTest {

    @Override
    protected void runTest() {
        setupApplication();

        MasterEventQueue master = new MasterEventQueue();
        MacroEvent macro = new MacroEvent("macro-1");

        SimpleEvent e1 = new SimpleEvent();
        SimpleEvent e2 = new SimpleEvent();
        macro.addEvent(e1);
        macro.addEvent(e2);

        AfterMacroEventCondition cond = new AfterMacroEventCondition(macro);

        /* 1 — macro not started → WAIT                                   */
        Assertions.assertSame(ConditionResult.WAIT,
            cond.evaluate(e1, master),
            "macro not started");

        /* 2 — first event executed, macro incomplete → still WAIT        */
        master.markExecuted(e1);
        Assertions.assertSame(ConditionResult.WAIT,
            cond.evaluate(e1, master),
            "macro half-done");

        /* 3 — all events executed → TRUE                                 */
        master.markExecuted(e2);
        Assertions.assertSame(ConditionResult.TRUE,
            cond.evaluate(e1, master),
            "macro completed");
    }
}
