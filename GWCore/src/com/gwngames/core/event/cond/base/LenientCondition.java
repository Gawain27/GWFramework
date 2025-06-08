package com.gwngames.core.event.cond.base;

import com.gwngames.core.api.event.IExecutionCondition;

/** Convenience base-class for “execute-unless-false” rules. */
public abstract class LenientCondition implements IExecutionCondition {
    @Override public final ConditionPolicy policy() {
        return ConditionPolicy.EXECUTE_UNLESS_FALSE;
    }
}
