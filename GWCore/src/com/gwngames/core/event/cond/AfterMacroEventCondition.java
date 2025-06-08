package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.queue.MasterEventQueue;

/**
 * Blocks the candidate event until the referenced macro-event has
 * finished all of its own events.
 * Behaviour type: WAIT_UNTIL_TRUE
 */
public final class AfterMacroEventCondition implements IExecutionCondition {

    private final MacroEvent macroEvent;

    public AfterMacroEventCondition(MacroEvent macroEvent) {
        this.macroEvent = macroEvent;
    }

    @Override
    public ConditionResult evaluate(IEvent event, MasterEventQueue master) {
        return master.isMacroEventCompleted(macroEvent)
            ? ConditionResult.TRUE   // ready – let the queue run the event
            : ConditionResult.WAIT;  // stay queued and re-check next tick
    }

    @Override
    public ConditionPolicy policy() {
        return ConditionPolicy.WAIT_UNTIL_TRUE;
    }
}
