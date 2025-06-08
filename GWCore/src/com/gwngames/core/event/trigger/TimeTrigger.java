package com.gwngames.core.event.trigger;

import com.badlogic.gdx.utils.TimeUtils;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.event.base.AbstractEventTrigger;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.queue.MasterEventQueue;

/**
 * Fires at fixed time intervals (in milliseconds).
 * If {@code repeat} is {@code false} it behaves like a one-shot timer.
 */
public class TimeTrigger extends AbstractEventTrigger {

    private final long intervalMs;
    private final boolean repeat;
    private final MacroEvent macroPayload;   // alternative to single event
    private final IEvent     singlePayload;  // alternative to macro
    private long nextFireAt;

    /* ► one-shot macro constructor */
    public TimeTrigger(String id, long delayMs, MacroEvent macro, MasterEventQueue master) {
        super(id, master);
        this.intervalMs   = delayMs;
        this.repeat       = false;
        this.macroPayload = macro;
        this.singlePayload = null;
        this.nextFireAt   = TimeUtils.millis() + delayMs;
    }

    /* ► repeated single-event constructor */
    public TimeTrigger(String id, long everyMs, IEvent event, boolean repeat, MasterEventQueue master) {
        super(id, master);
        this.intervalMs   = everyMs;
        this.repeat       = repeat;
        this.singlePayload = event;
        this.macroPayload  = null;
        this.nextFireAt    = TimeUtils.millis() + everyMs;
    }

    @Override
    public boolean pollAndFire(float delta) {

        if (!isEnabled()) return false;

        long now = TimeUtils.millis();
        if (now < nextFireAt) return false;

        /* -- enqueue payload -- */
        if (macroPayload != null)
            masterEventQueue.enqueueMacroEvent(macroPayload);
        else
            masterEventQueue.enqueueEvent(singlePayload);

        /* -- schedule next tick -- */
        if (repeat) {
            nextFireAt = now + intervalMs;
        } else {
            setEnabled(false);             // one-shot complete
        }
        return true;
    }
}
