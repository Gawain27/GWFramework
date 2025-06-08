package com.gwngames.core.event.cond.base;

/** Return value of an {@code IExecutionCondition}. */
public enum ConditionResult {
    /** Condition is satisfied – the event may proceed.            */
    TRUE,

    /** Condition is *not yet* satisfied – keep the event queued.  */
    WAIT,

    /** Condition actively vetoes execution.                       */
    FALSE
}
