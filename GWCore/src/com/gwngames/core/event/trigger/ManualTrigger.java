package com.gwngames.core.event.trigger;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.event.base.AbstractEventTrigger;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.queue.MasterEventQueue;

/** Fires only when {@link #fire()} is called. */
public class ManualTrigger extends AbstractEventTrigger {

    private MacroEvent macroPayload;
    private IEvent     singlePayload;
    private volatile boolean pending = false;

    public ManualTrigger(String id, MasterEventQueue master) { super(id, master); }

    /* setters so external code can change the payload */
    public void setMacroPayload (MacroEvent m) { this.macroPayload  = m; }
    public void setSinglePayload(IEvent e)     { this.singlePayload = e; }

    /** Mark the trigger to enqueue its payload on the next poll. */
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
