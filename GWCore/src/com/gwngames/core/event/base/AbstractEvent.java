package com.gwngames.core.event.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.EventStatus;
import com.gwngames.core.api.event.IEvent;

public abstract class AbstractEvent implements IEvent {
    private MacroEvent macroEvent;
    private EventStatus status = EventStatus.WAITING;
    private long executionStartTime;
    private long executionDuration;

    public MacroEvent getMacroEvent() { return macroEvent; }
    public void setMacroEvent(MacroEvent macroEvent) { this.macroEvent = macroEvent; }

    public EventStatus getStatus() { return status; }

    public void setStatus(EventStatus status) {
        this.status = status;
        if (status == EventStatus.EXECUTING) executionStartTime = System.currentTimeMillis();
        else if (status == EventStatus.COMPLETED) executionDuration = System.currentTimeMillis() - executionStartTime;
    }

    public long getExecutionDuration() { return executionDuration; }
}
