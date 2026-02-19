package com.gwngames.core.event.trigger;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.trigger.IManualTrigger;
import com.gwngames.core.event.base.AbstractEventTrigger;
import com.gwngames.core.event.base.MacroEvent;

/** Fires only when {@link #fire()} is called. */
@Init(module = CoreModule.CORE)
public class ManualTrigger extends AbstractEventTrigger implements IManualTrigger {

    private MacroEvent macroPayload;
    private IEvent     singlePayload;
    private volatile boolean pending = false;

    /* setters so external code can change the payload */
    public void setMacroPayload (MacroEvent m) { this.macroPayload  = m; }

    @Override
    public void setSinglePayload(IEvent e)     { this.singlePayload = e; }

    /** Mark the trigger to enqueue its payload on the next poll. */
    @Override
    public void fire() { pending = true; }

    @Override
    public boolean pollAndFire(float delta) {

        if (!isEnabled() || !pending) return false;

        pending = false;
        if (macroPayload != null)
            masterEventQueue.enqueueMacroEvent(macroPayload);
        else if (singlePayload != null)
            masterEventQueue.enqueueEvent(singlePayload);

        return true;
    }
}
