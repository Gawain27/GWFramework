package com.gwngames.core.api.event;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.base.MacroEvent;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.MASTER_EVENT_QUEUE)
public interface IMasterEventQueue extends IBaseComp {
    void enqueueMacroEvent(IMacroEvent macroEvent);

    /* ───────────── helper: enqueue *single* event ───────────── */
    void enqueueEvent(IEvent ev);

    boolean canExecute(IEvent ev);

    void markExecuted(IEvent event);

    boolean hasExecuted(IEvent event);

    boolean isMacroEventCompleted(IMacroEvent macroEvent);

    Array<IMacroEvent> getMacroEvents();

    FileLogger getLogger();

    void handleEventException(IEvent event, EventException ex);
}
