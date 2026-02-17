package com.gwngames.core.data.event;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEventStatus;

@Init(module = CoreModule.CORE)
public enum EventStatus implements IEventStatus {
    WAITING,
    EXECUTING,
    COMPLETED;
}
