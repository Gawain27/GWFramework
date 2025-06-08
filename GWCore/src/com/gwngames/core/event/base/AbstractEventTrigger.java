package com.gwngames.core.event.base;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IEventTrigger;
import com.gwngames.core.event.queue.MasterEventQueue;

import java.util.UUID;

/** Simple base class that takes care of id + enabled flag.<p>
* Sub-classes implement the real firing logic.
*/
public abstract class AbstractEventTrigger implements IEventTrigger {

    private final String id;
    private volatile boolean enabled = true;

    protected final MasterEventQueue masterEventQueue;

    protected AbstractEventTrigger(String id, MasterEventQueue masterEventQueue) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.masterEventQueue = masterEventQueue;
    }

    @Override public final String  getId()            { return id;       }
    @Override public final boolean isEnabled()        { return enabled;  }
    @Override public final void    setEnabled(boolean e) { enabled = e;  }
}
