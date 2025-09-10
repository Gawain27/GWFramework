package com.gwngames.core.data.event;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEventStatus;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE)
public enum EventStatus implements IEventStatus {
    WAITING,
    EXECUTING,
    COMPLETED;
}
