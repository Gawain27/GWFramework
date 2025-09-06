package com.gwngames.core.event.cond.base;

import com.gwngames.core.api.event.IExecutionCondition;

/** Convenience base-class for “wait-until-true” rules. */
public abstract class StrictCondition implements IExecutionCondition {
    @Override
    public final ConditionPolicy policy() {
        return ConditionPolicy.WAIT_UNTIL_TRUE;
    }
}
