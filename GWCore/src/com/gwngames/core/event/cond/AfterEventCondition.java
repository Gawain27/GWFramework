package com.gwngames.core.event.cond;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IConditionResult;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.event.cond.base.StrictCondition;

import static com.gwngames.core.event.cond.base.ConditionResult.TRUE;
import static com.gwngames.core.event.cond.base.ConditionResult.WAIT;

@Init(module = CoreModule.CORE)
public class AfterEventCondition extends StrictCondition {
    private IEvent prerequisite;

    @Override
    public IConditionResult evaluate(IEvent e, IMasterEventQueue q) {
        return q.hasExecuted(prerequisite) ? TRUE : WAIT;
    }

    public void setPrerequisite(IEvent prerequisite) {
        this.prerequisite = prerequisite;
    }
}
