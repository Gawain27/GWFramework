package com.gwngames.core.api.event;


import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.queue.MasterEventQueue;

public interface IExecutionCondition {
    /** Return the current result for *(event, queue)*.     */
    ConditionResult evaluate(IEvent event, MasterEventQueue queue);

    /** Default policy can be overridden per-condition.     */
    ConditionPolicy policy();
}
