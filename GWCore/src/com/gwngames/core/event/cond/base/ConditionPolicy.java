package com.gwngames.core.event.cond.base;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IConditionPolicy;

/**
 * Determines how the engine interprets a {@link ConditionResult}.
 *
 * <ul>
 *   <li>{@code WAIT_UNTIL_TRUE} – the event runs <em>only</em> when
 *       the condition returns {@link ConditionResult#TRUE}.</li>
 *   <li>{@code EXECUTE_UNLESS_FALSE} – the event runs unless the
 *       condition returns {@link ConditionResult#FALSE}.</li>
 * </ul>
 */
@Init(module = CoreModule.CORE)
public enum ConditionPolicy implements IConditionPolicy {
    WAIT_UNTIL_TRUE,
    EXECUTE_UNLESS_FALSE
}
