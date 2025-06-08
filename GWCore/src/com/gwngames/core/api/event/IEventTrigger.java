package com.gwngames.core.api.event;

import com.gwngames.core.event.queue.MasterEventQueue;

/**
 * A hook that decides – each frame/tick – whether it should
 * inject one or more events/macro-events into the queue.
 */
public interface IEventTrigger {

    /** Unique identifier (used for enable/disable at runtime). */
    String getId();

    /** Whether the trigger is currently active. */
    boolean isEnabled();
    void    setEnabled(boolean enabled);

    /**
     * Called once per {@link MasterEventQueue#process(float)}.
     *
     * @param queue the central queue so the trigger can enqueue payloads
     * @param delta time since the previous call (seconds)
     * @return {@code true} if the trigger fired this tick
     */
    boolean pollAndFire(float delta);
}
