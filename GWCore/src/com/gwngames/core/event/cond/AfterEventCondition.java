package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.event.queue.MasterEventQueue;

public class AfterEventCondition implements IExecutionCondition {
    private final IEvent prerequisite;

    public AfterEventCondition(IEvent prerequisite) { this.prerequisite = prerequisite; }

    @Override
    public boolean evaluate(IEvent event, MasterEventQueue masterQueue) {
        return masterQueue.hasExecuted(prerequisite);
    }
}
