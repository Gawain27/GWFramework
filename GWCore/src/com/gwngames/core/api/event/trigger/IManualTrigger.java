package com.gwngames.core.api.event.trigger;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.data.ComponentNames;

@Init(component = ComponentNames.MANUAL_TRIGGER)
public interface IManualTrigger extends IEventTrigger {
    void setSinglePayload(IEvent e);

    void fire();
}
