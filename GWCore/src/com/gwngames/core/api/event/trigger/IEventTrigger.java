package com.gwngames.core.api.event.trigger;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.event.queue.MasterEventQueue;

/**
 * A hook that decides – each frame/tick – whether it should
 * inject one or more events/macro-events into the queue.
 */
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.EVENT_TRIGGER, allowMultiple = true)
public interface IEventTrigger extends IBaseComp {

    /** Unique identifier (used for enable/disable at runtime). */
    String getId();

    /** Whether the trigger is currently active. */
    boolean isEnabled();
    void    setEnabled(boolean enabled);

    /**
     * Called once per {@link MasterEventQueue#process(float)}.
     *
     * @param delta time since the previous call (seconds)
     * @return {@code true} if the trigger fired this tick
     */
    boolean pollAndFire(float delta);
}
