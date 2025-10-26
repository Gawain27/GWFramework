package com.gwngames.core.api.event;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.trigger.IEventTrigger;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.MASTER_EVENT_QUEUE)
public interface IMasterEventQueue extends IBaseComp {
    void registerQueue(IEventQueue q);

    void enqueueMacroEvent(IMacroEvent macroEvent);

    void enqueueEvent(IEvent ev);

    boolean canExecute(IEvent ev);

    void markExecuted(IEvent event);

    boolean hasExecuted(IEvent event);

    boolean isMacroEventCompleted(IMacroEvent macroEvent);

    List<IMacroEvent> getMacroEvents();

    void process(float delta);

    FileLogger getLogger();

    void handleEventException(IEvent event, EventException ex);

    void registerTrigger(IEventTrigger t);

    void removeTrigger(String id);

    void enableTrigger(String id);

    void disableTrigger(String id);
}
