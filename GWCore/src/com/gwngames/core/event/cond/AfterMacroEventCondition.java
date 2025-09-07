package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.*;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;

/**
 * Blocks the candidate event until the referenced macro-event has
 * finished all of its own events.
 * Behaviour type: WAIT_UNTIL_TRUE
 */
public final class AfterMacroEventCondition implements IExecutionCondition {

    private final IMacroEvent macroEvent;

    public AfterMacroEventCondition(IMacroEvent macroEvent) {
        this.macroEvent = macroEvent;
    }

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
}
