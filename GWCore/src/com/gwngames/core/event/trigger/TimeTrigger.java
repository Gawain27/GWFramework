package com.gwngames.core.event.trigger;

import com.badlogic.gdx.utils.TimeUtils;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.api.event.trigger.ITimeTrigger;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.base.AbstractEventTrigger;

/**
 * Fires at fixed time intervals (in milliseconds).
 * If {@code repeat} is {@code false} it behaves like a one-shot timer.
 */
@Init(module = ModuleNames.CORE)
public class TimeTrigger extends AbstractEventTrigger implements ITimeTrigger {

    private long intervalMs;
    private boolean repeat;
    private IMacroEvent macroPayload;   // alternative to single event
    private IEvent singlePayload;  // alternative to macro
    private long nextFireAt;

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

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public IMacroEvent getMacroPayload() {
        return macroPayload;
    }

    public void setMacroPayload(IMacroEvent macroPayload) {
        this.macroPayload = macroPayload;
    }

    public IEvent getSinglePayload() {
        return singlePayload;
    }

    public void setSinglePayload(IEvent singlePayload) {
        this.singlePayload = singlePayload;
    }

    public long getNextFireAt() {
        return nextFireAt;
    }

    public void setNextFireAt(long nextFireAt) {
        this.nextFireAt = nextFireAt;
    }
}
