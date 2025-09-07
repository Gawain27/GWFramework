package com.gwngames.core.data.event;

import com.gwngames.core.api.event.IEventStatus;

public enum EventStatus implements IEventStatus {
    WAITING,
    EXECUTING,
    COMPLETED;
}
