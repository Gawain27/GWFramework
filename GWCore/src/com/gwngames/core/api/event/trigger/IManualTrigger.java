package com.gwngames.core.api.event.trigger;

import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;

@Init(component = CoreComponent.MANUAL_TRIGGER)
public interface IManualTrigger extends IEventTrigger {
    void setSinglePayload(IEvent e);

    void fire();
}
