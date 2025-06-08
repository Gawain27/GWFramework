package com.gwngames.core.event.base;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.event.EventStatus;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;

public abstract class AbstractEvent implements IEvent {
    private MacroEvent macroEvent;
    private EventStatus status = EventStatus.WAITING;
    private long executionStartTime;
    private long executionDuration;
    private final Array<IExecutionCondition> conditions = new Array<>();

    @Override
    public MacroEvent getMacroEvent() { return macroEvent; }
    @Override
    public void setMacroEvent(MacroEvent macroEvent) { this.macroEvent = macroEvent; }

    public EventStatus getStatus() { return status; }

    public void setStatus(EventStatus status) {
        this.status = status;
        if (status == EventStatus.EXECUTING) executionStartTime = System.currentTimeMillis();
        else if (status == EventStatus.COMPLETED) executionDuration = System.currentTimeMillis() - executionStartTime;
    }

    public long getExecutionDuration() { return executionDuration; }


    /** Attach a dependency rule to <em>this</em> event. */
    @Override
    public void addCondition(IExecutionCondition c) {
        conditions.add(c);
    }

    /** Iterate over all rules attached to this event. */
    @Override
    public Iterable<IExecutionCondition> getConditions() {
        return conditions;
    }
}
