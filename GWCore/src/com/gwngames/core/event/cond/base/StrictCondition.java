package com.gwngames.core.event.cond.base;

import com.gwngames.core.api.event.IConditionPolicy;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.base.BaseComponent;

/** Convenience base-class for “wait-until-true” rules. */
public abstract class StrictCondition extends BaseComponent implements IExecutionCondition {
    @Override
    public final IConditionPolicy policy() {
        return ConditionPolicy.WAIT_UNTIL_TRUE;
    }
}
