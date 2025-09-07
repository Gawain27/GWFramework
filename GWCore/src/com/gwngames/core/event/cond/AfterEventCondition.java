package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.IConditionResult;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.cond.base.StrictCondition;

import static com.gwngames.core.event.cond.base.ConditionResult.TRUE;
import static com.gwngames.core.event.cond.base.ConditionResult.WAIT;

public final class AfterEventCondition extends StrictCondition {
    private final IEvent prerequisite;
    public AfterEventCondition(IEvent prerequisite) { this.prerequisite = prerequisite; }

    @Override
    public IConditionResult evaluate(IEvent e, IMasterEventQueue q) {
        return q.hasExecuted(prerequisite) ? TRUE : WAIT;
    }
}
