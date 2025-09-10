package com.gwngames.core.event.cond;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;

/**
 * Blocks the candidate event until the referenced macro-event has
 * finished all of its own events.
 * Behaviour type: WAIT_UNTIL_TRUE
 */
@Init(module = ModuleNames.CORE)
public final class AfterMacroEventCondition extends BaseComponent implements IExecutionCondition {
    private IMacroEvent macroEvent;

    @Override
    public IConditionResult evaluate(IEvent event, IMasterEventQueue master) {
        return master.isMacroEventCompleted(macroEvent)
            ? ConditionResult.TRUE   // ready – let the queue run the event
            : ConditionResult.WAIT;  // stay queued and re-check next tick
    }

    @Override
    public IConditionPolicy policy() {
        return ConditionPolicy.WAIT_UNTIL_TRUE;
    }

    public void setMacroEvent(IMacroEvent macroEvent) {
        this.macroEvent = macroEvent;
    }
}
