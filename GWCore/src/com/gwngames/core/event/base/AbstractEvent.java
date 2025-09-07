package com.gwngames.core.event.base;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.event.IEventStatus;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.base.BaseComponent;

public abstract class AbstractEvent extends BaseComponent implements IEvent {
    private IMacroEvent macroEvent;
    private IEventStatus status = EventStatus.WAITING;
    private long executionStartTime;
    private long executionDuration;
    private final Array<IExecutionCondition> conditions = new Array<>();

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
