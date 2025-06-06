package com.gwngames.core.event.cond;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.queue.MasterEventQueue;

public class AfterMacroEventCondition implements IExecutionCondition {
    private final MacroEvent macroEvent;

    public AfterMacroEventCondition(MacroEvent macroEvent) { this.macroEvent = macroEvent; }

    @Override
    public boolean evaluate(IEvent event, MasterEventQueue masterQueue) {
        return masterQueue.isMacroEventCompleted(macroEvent);
    }
}
