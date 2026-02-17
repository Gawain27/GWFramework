package com.gwngames.core.api.event;


import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

@Init(component = CoreComponent.EXECUTION_CONDITION, module = DefaultModule.INTERFACE, allowMultiple = true)
public interface IExecutionCondition extends IBaseComp {
    /** Return the current result for *(event, queue)*.     */
    IConditionResult evaluate(IEvent event, IMasterEventQueue queue);

    /** Default policy can be overridden per-condition.     */
    IConditionPolicy policy();
}
