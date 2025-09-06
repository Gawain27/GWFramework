package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.cond.base.StrictCondition;
import com.gwngames.core.event.queue.MasterEventQueue;

import static com.gwngames.core.event.cond.base.ConditionResult.TRUE;
import static com.gwngames.core.event.cond.base.ConditionResult.WAIT;

public final class AfterEventCondition extends StrictCondition {
    private final IEvent prerequisite;
    public AfterEventCondition(IEvent prerequisite) { this.prerequisite = prerequisite; }

    @Override
    public ConditionResult evaluate(IEvent e, MasterEventQueue q) {
        return q.hasExecuted(prerequisite) ? TRUE : WAIT;
    }
}
