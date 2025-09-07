package com.gwngames.core.event.cond.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IConditionResult;
import com.gwngames.core.data.ModuleNames;

/** Return value of an {@code IExecutionCondition}. */
@Init(module = ModuleNames.CORE)
public enum ConditionResult implements IConditionResult {
    /** Condition is satisfied – the event may proceed.            */
    TRUE,

    /** Condition is *not yet* satisfied – keep the event queued.  */
    WAIT,

    /** Condition actively vetoes execution.                       */
    FALSE
}
