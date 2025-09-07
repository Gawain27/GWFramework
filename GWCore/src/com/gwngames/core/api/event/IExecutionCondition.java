package com.gwngames.core.api.event;


import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.queue.MasterEventQueue;

@Init(component = ComponentNames.EXECUTION_CONDITION, module = ModuleNames.INTERFACE)
public interface IExecutionCondition extends IBaseComp {
    /** Return the current result for *(event, queue)*.     */
    IConditionResult evaluate(IEvent event, IMasterEventQueue queue);

    /** Default policy can be overridden per-condition.     */
    IConditionPolicy policy();
}
