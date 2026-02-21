package com.gwngames.core.event.base;

import com.gwngames.core.api.event.IEventStatus;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.base.BaseComponent;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractEvent extends BaseComponent implements IEvent {
    private IMacroEvent macroEvent;
    private IEventStatus status = EventStatus.WAITING;
    private long executionStartTime;
    private long executionDuration;
    private final List<IExecutionCondition> conditions = new ArrayList<>();

    @Override
    public IMacroEvent getMacroEvent() { return macroEvent; }
    @Override
    public void setMacroEvent(IMacroEvent macroEvent) { this.macroEvent = macroEvent; }

    @Override
    public IEventStatus getStatus() { return status; }

    @Override
    public void setStatus(IEventStatus status) {
        this.status = status;
        if (status == EventStatus.EXECUTING) executionStartTime = System.currentTimeMillis();
        else if (status.isFinalState()) executionDuration = System.currentTimeMillis() - executionStartTime;
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
