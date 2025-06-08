package com.gwngames.core.event.cond.base;

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
public enum ConditionPolicy {
    WAIT_UNTIL_TRUE,
    EXECUTE_UNLESS_FALSE
}
